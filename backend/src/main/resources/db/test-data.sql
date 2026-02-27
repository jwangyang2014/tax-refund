/*
1. adds 14 new users in different cities/states
2. uses the same password_hash as user id = 2
3. creates data for all selected users including user 2 seeds 2020–2025
4. makes 2020–2024 mostly terminal (AVAILABLE / REJECTED)
5. makes 2025 status randomly one of RECEIVED, PROCESSING, APPROVED, SENT, AVAILABLE, REJECTED
6. creates realistic event progressions, filing dates, amounts, and timings

A practical note: this script deletes and recreates refund_record and refund_status_event rows for the seeded users for tax years 2020–2025 so you can rerun it.

Seed realistic refund history + outbox + ETA predictions for:
- existing user id = 2
- 14 generated users

Workflow:
1) run this script once
2) call ML /train
3) update seed_model.model_version below to actual /model/info version
4) rerun this script

Notes:
- Safe to rerun: deletes/recreates seeded refund data for 2020–2025
- Seeds outbox_event as already processed (realistic historical pipeline)
- Seeds refund_eta_prediction with model_name/model_version aligned to your ML service
*/

BEGIN;

SELECT setseed(0.4242);

DROP TABLE IF EXISTS seed_users;
DROP TABLE IF EXISTS refund_seed;
DROP TABLE IF EXISTS latest_status_event;
DROP TABLE IF EXISTS seed_model;

-- =========================================================
-- 0) Ensure user id = 2 exists
-- =========================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM app_user WHERE id = 2) THEN
        RAISE EXCEPTION 'app_user with id=2 does not exist';
    END IF;
END $$;

-- =========================================================
-- 0.5) Model info for refund_eta_prediction
--      Replace model_version AFTER /train + /model/info
-- =========================================================
CREATE TEMP TABLE seed_model AS
SELECT
    'gbrt'::varchar AS model_name,
    '20260227T162154Z'::varchar AS model_version;

-- =========================================================
-- 1) Add 14 users, reusing password_hash from app_user.id = 2
-- =========================================================
WITH pwd AS (
    SELECT password_hash
    FROM app_user
    WHERE id = 2
),
new_users(email, first_name, last_name, address, city, state, phone, role) AS (
    VALUES
        ('olivia.chen@example.com',    'Olivia',   'Chen',     '101 Market St',      'San Francisco', 'CA', '415-555-0101', 'USER'),
        ('ethan.nguyen@example.com',   'Ethan',    'Nguyen',   '2200 Mission St',    'San Jose',      'CA', '408-555-0102', 'USER'),
        ('mia.patel@example.com',      'Mia',      'Patel',    '88 Castro St',       'Mountain View', 'CA', '650-555-0103', 'USER'),
        ('liam.kim@example.com',       'Liam',     'Kim',      '14 University Ave',  'Palo Alto',     'CA', '650-555-0104', 'USER'),
        ('ava.garcia@example.com',     'Ava',      'Garcia',   '901 5th Ave',        'Seattle',       'WA', '206-555-0105', 'USER'),
        ('noah.brown@example.com',     'Noah',     'Brown',    '77 W Washington St', 'Chicago',       'IL', '312-555-0106', 'USER'),
        ('emma.johnson@example.com',   'Emma',     'Johnson',  '1201 Main St',       'Dallas',        'TX', '214-555-0107', 'USER'),
        ('lucas.davis@example.com',    'Lucas',    'Davis',    '210 Peachtree St',   'Atlanta',       'GA', '404-555-0108', 'USER'),
        ('sophia.martin@example.com',  'Sophia',   'Martin',   '500 Boylston St',    'Boston',        'MA', '617-555-0109', 'USER'),
        ('henry.wilson@example.com',   'Henry',    'Wilson',   '400 Orange Ave',     'Orlando',       'FL', '407-555-0110', 'USER'),
        ('isabella.moore@example.com', 'Isabella', 'Moore',    '1600 Broadway',      'New York',      'NY', '212-555-0111', 'USER'),
        ('james.taylor@example.com',   'James',    'Taylor',   '123 Fremont St',     'Las Vegas',     'NV', '702-555-0112', 'USER'),
        ('amelia.thomas@example.com',  'Amelia',   'Thomas',   '99 Colfax Ave',      'Denver',        'CO', '303-555-0113', 'USER'),
        ('benjamin.lee@example.com',   'Benjamin', 'Lee',      '700 N Central Ave',  'Phoenix',       'AZ', '602-555-0114', 'USER')
)
INSERT INTO app_user (
    email, password_hash, role, created_at,
    first_name, last_name, address, city, state, phone
)
SELECT
    nu.email,
    pwd.password_hash,
    nu.role,
    now() - ((60 + floor(random() * 1000))::int || ' days')::interval,
    nu.first_name,
    nu.last_name,
    nu.address,
    nu.city,
    nu.state,
    nu.phone
FROM new_users nu
CROSS JOIN pwd
ON CONFLICT (email) DO NOTHING;

-- =========================================================
-- 2) Seed user set = existing id=2 + inserted users
-- =========================================================
CREATE TEMP TABLE seed_users AS
SELECT
    id,
    email,
    COALESCE(first_name, 'User') AS first_name,
    COALESCE(last_name, 'Two') AS last_name,
    COALESCE(city, 'Mountain View') AS city,
    COALESCE(state, 'CA') AS state,
    created_at
FROM app_user
WHERE id = 2
   OR email IN (
        'olivia.chen@example.com',
        'ethan.nguyen@example.com',
        'mia.patel@example.com',
        'liam.kim@example.com',
        'ava.garcia@example.com',
        'noah.brown@example.com',
        'emma.johnson@example.com',
        'lucas.davis@example.com',
        'sophia.martin@example.com',
        'henry.wilson@example.com',
        'isabella.moore@example.com',
        'james.taylor@example.com',
        'amelia.thomas@example.com',
        'benjamin.lee@example.com'
   );

-- =========================================================
-- 3) Rerunnable cleanup for seeded users / years
-- =========================================================
DELETE FROM refund_eta_prediction rep
USING seed_users su
WHERE rep.user_id = su.id
  AND rep.tax_year BETWEEN 2020 AND 2025;

DELETE FROM outbox_event oe
USING seed_users su
WHERE (
    (oe.payload->>'userId')::bigint = su.id
    AND (oe.payload->>'taxYear')::int BETWEEN 2020 AND 2025
)
OR oe.aggregate_key LIKE su.id::text || ':%';

DELETE FROM refund_status_event rse
USING seed_users su
WHERE rse.user_id = su.id
  AND rse.tax_year BETWEEN 2020 AND 2025;

DELETE FROM refund_record rr
USING seed_users su
WHERE rr.user_id = su.id
  AND rr.tax_year BETWEEN 2020 AND 2025;

-- =========================================================
-- 4) Build one scenario per user per tax year
--
-- Filing happens in year tax_year + 1.
-- 2020-2024: mostly AVAILABLE, some REJECTED.
-- 2025: mixture of all 6 statuses.
-- =========================================================
CREATE TEMP TABLE refund_seed AS
WITH base AS (
    SELECT
        su.id AS user_id,
        gs.tax_year,
        su.state AS filing_state,

        ROUND((
            CASE
                WHEN gs.tax_year IN (2020, 2021) THEN 250 + random() * 1600
                WHEN gs.tax_year IN (2022, 2023) THEN 300 + random() * 2100
                ELSE 350 + random() * 2600
            END
        )::numeric, 2) AS expected_amount,

        make_timestamp(
            gs.tax_year + 1,
            2 + floor(random() * 3)::int,          -- Feb-Apr
            1 + floor(random() * 27)::int,         -- 1..28
            8 + floor(random() * 9)::int,          -- 08..16
            floor(random() * 60)::int,
            floor(random() * 60)::double precision
        ) AS filed_at,

        CASE
            WHEN gs.tax_year <= 2021 THEN 'BACKFILL'
            WHEN gs.tax_year <= 2023 THEN CASE WHEN random() < 0.75 THEN 'IRS' ELSE 'BACKFILL' END
            ELSE CASE WHEN random() < 0.90 THEN 'IRS' ELSE 'SIMULATION' END
        END AS source,

        CASE
            WHEN gs.tax_year < 2025 THEN
                CASE
                    WHEN random() < 0.85 THEN 'AVAILABLE'
                    ELSE 'REJECTED'
                END
            ELSE
                CASE
                    WHEN random() < 0.12 THEN 'RECEIVED'
                    WHEN random() < 0.42 THEN 'PROCESSING'
                    WHEN random() < 0.62 THEN 'APPROVED'
                    WHEN random() < 0.76 THEN 'SENT'
                    WHEN random() < 0.92 THEN 'AVAILABLE'
                    ELSE 'REJECTED'
                END
        END AS final_status,

        ('IRS-' || gs.tax_year || '-' || lpad(su.id::text, 6, '0') || '-' ||
         substr(md5(su.id::text || '-' || gs.tax_year::text || '-' || random()::text), 1, 8)
        ) AS irs_tracking_id,

        (1 + floor(random() * 4)::int)  AS d_received_to_processing,
        (4 + floor(random() * 14)::int) AS d_processing_to_approved,
        (1 + floor(random() * 5)::int)  AS d_approved_to_sent,
        (1 + floor(random() * 5)::int)  AS d_sent_to_available,
        (2 + floor(random() * 10)::int) AS d_processing_to_rejected
    FROM seed_users su
    CROSS JOIN generate_series(2020, 2025) AS gs(tax_year)
)
SELECT
    b.*,
    b.filed_at + make_interval(days => b.d_received_to_processing) AS processing_at,
    b.filed_at + make_interval(days => b.d_received_to_processing + b.d_processing_to_approved) AS approved_at,
    b.filed_at + make_interval(days => b.d_received_to_processing + b.d_processing_to_approved + b.d_approved_to_sent) AS sent_at,
    b.filed_at + make_interval(days => b.d_received_to_processing + b.d_processing_to_approved + b.d_approved_to_sent + b.d_sent_to_available) AS available_at,
    b.filed_at + make_interval(days => b.d_received_to_processing + b.d_processing_to_rejected) AS rejected_at
FROM base b;

-- =========================================================
-- 5) Insert refund_status_event event sequences
-- =========================================================
INSERT INTO refund_status_event (
    user_id,
    tax_year,
    filing_state,
    from_status,
    to_status,
    expected_amount,
    irs_tracking_id,
    source,
    occurred_at
)
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, NULL, 'RECEIVED',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.filed_at
FROM refund_seed rs

UNION ALL
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, 'RECEIVED', 'PROCESSING',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.processing_at
FROM refund_seed rs
WHERE rs.final_status IN ('PROCESSING', 'APPROVED', 'SENT', 'AVAILABLE', 'REJECTED')

UNION ALL
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, 'PROCESSING', 'APPROVED',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.approved_at
FROM refund_seed rs
WHERE rs.final_status IN ('APPROVED', 'SENT', 'AVAILABLE')

UNION ALL
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, 'APPROVED', 'SENT',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.sent_at
FROM refund_seed rs
WHERE rs.final_status IN ('SENT', 'AVAILABLE')

UNION ALL
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, 'SENT', 'AVAILABLE',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.available_at
FROM refund_seed rs
WHERE rs.final_status = 'AVAILABLE'

UNION ALL
SELECT
    rs.user_id, rs.tax_year, rs.filing_state, 'PROCESSING', 'REJECTED',
    rs.expected_amount, rs.irs_tracking_id, rs.source, rs.rejected_at
FROM refund_seed rs
WHERE rs.final_status = 'REJECTED';

-- =========================================================
-- 6) Insert refund_record latest snapshot
-- =========================================================
INSERT INTO refund_record (
    user_id,
    tax_year,
    status,
    last_updated_at,
    expected_amount,
    irs_tracking_id,
    available_at_estimated
)
SELECT
    rs.user_id,
    rs.tax_year,
    rs.final_status,
    CASE rs.final_status
        WHEN 'RECEIVED'   THEN rs.filed_at
        WHEN 'PROCESSING' THEN rs.processing_at
        WHEN 'APPROVED'   THEN rs.approved_at
        WHEN 'SENT'       THEN rs.sent_at
        WHEN 'AVAILABLE'  THEN rs.available_at
        WHEN 'REJECTED'   THEN rs.rejected_at
    END,
    rs.expected_amount,
    rs.irs_tracking_id,
    CASE rs.final_status
        WHEN 'AVAILABLE'  THEN rs.available_at
        WHEN 'SENT'       THEN rs.sent_at + interval '3 days'
        WHEN 'APPROVED'   THEN rs.approved_at + interval '6 days'
        WHEN 'PROCESSING' THEN rs.processing_at + interval '12 days'
        WHEN 'RECEIVED'   THEN rs.filed_at + interval '20 days'
        ELSE NULL
    END
FROM refund_seed rs
ORDER BY rs.user_id, rs.tax_year;

-- =========================================================
-- 7) Latest status event per user/year
--    Used to seed realistic processed outbox rows
-- =========================================================
CREATE TEMP TABLE latest_status_event AS
SELECT DISTINCT ON (rse.user_id, rse.tax_year)
    rse.user_id,
    rse.tax_year,
    rse.filing_state,
    rse.to_status AS status,
    rse.expected_amount,
    rse.irs_tracking_id,
    rse.occurred_at
FROM refund_status_event rse
JOIN seed_users su
  ON su.id = rse.user_id
WHERE rse.tax_year BETWEEN 2020 AND 2025
ORDER BY rse.user_id, rse.tax_year, rse.occurred_at DESC;

-- =========================================================
-- 8) Seed outbox_event
--    Historical rows are already processed
-- =========================================================
INSERT INTO outbox_event (
    event_type,
    aggregate_key,
    payload,
    created_at,
    processed_at,
    attempts,
    last_error
)
SELECT
    'REFUND_STATUS_UPDATED',
    lse.user_id::text || ':' || lse.tax_year::text,
    jsonb_build_object(
        'userId', lse.user_id,
        'taxYear', lse.tax_year,
        'filingState', lse.filing_state,
        'status', lse.status,
        'expectedAmount', lse.expected_amount,
        'trackingId', lse.irs_tracking_id
    ),
    lse.occurred_at + interval '2 minutes',
    lse.occurred_at + interval '2 minutes' + make_interval(secs => (5 + floor(random() * 90))::int),
    0,
    NULL
FROM latest_status_event lse
ORDER BY lse.user_id, lse.tax_year;

-- =========================================================
-- 9) Seed refund_eta_prediction
--
-- Logic:
-- - AVAILABLE -> eta_days = 0
-- - Historical AVAILABLE paths -> exact remaining days from each status event
-- - Current non-terminal / rejected paths -> plausible heuristic estimates
--
-- model_name/model_version come from seed_model
-- IMPORTANT: replace model_version after /train + /model/info
-- =========================================================
WITH available_targets AS (
    SELECT
        rse.user_id,
        rse.tax_year,
        max(CASE WHEN rse.to_status = 'AVAILABLE' THEN rse.occurred_at END) AS available_at
    FROM refund_status_event rse
    JOIN seed_users su
      ON su.id = rse.user_id
    WHERE rse.tax_year BETWEEN 2020 AND 2025
    GROUP BY rse.user_id, rse.tax_year
),
latest_by_status AS (
    SELECT DISTINCT ON (rse.user_id, rse.tax_year, rse.to_status)
        rse.user_id,
        rse.tax_year,
        rse.filing_state,
        rse.to_status AS status,
        rse.expected_amount,
        rse.occurred_at
    FROM refund_status_event rse
    JOIN seed_users su
      ON su.id = rse.user_id
    WHERE rse.tax_year BETWEEN 2020 AND 2025
      AND rse.to_status IN ('RECEIVED', 'PROCESSING', 'APPROVED', 'SENT', 'AVAILABLE')
    ORDER BY rse.user_id, rse.tax_year, rse.to_status, rse.occurred_at DESC
),
prediction_base AS (
    SELECT
        lbs.user_id,
        lbs.tax_year,
        lbs.filing_state,
        lbs.status,
        lbs.expected_amount,
        lbs.occurred_at,
        at.available_at,
        CASE
            WHEN lbs.status = 'AVAILABLE' THEN 0

            -- exact remaining days for cases that eventually became AVAILABLE
            WHEN at.available_at IS NOT NULL THEN
                GREATEST(
                    0,
                    CEIL(EXTRACT(EPOCH FROM (at.available_at - lbs.occurred_at)) / 86400.0)::int
                )

            -- heuristic fallback for current non-terminal 2025 rows without actual AVAILABLE yet
            WHEN lbs.status = 'SENT' THEN 2 + CASE WHEN lbs.filing_state IN ('CA','NY') THEN 1 ELSE 0 END
            WHEN lbs.status = 'APPROVED' THEN 5 + CASE WHEN lbs.expected_amount > 2000 THEN 2 ELSE 0 END
            WHEN lbs.status = 'PROCESSING' THEN 10 + CASE WHEN lbs.filing_state IN ('CA','NY','IL') THEN 2 ELSE 0 END
            WHEN lbs.status = 'RECEIVED' THEN 16 + CASE WHEN lbs.expected_amount > 2000 THEN 3 ELSE 0 END
            ELSE 0
        END AS eta_days
    FROM latest_by_status lbs
    LEFT JOIN available_targets at
      ON at.user_id = lbs.user_id
     AND at.tax_year = lbs.tax_year
)
INSERT INTO refund_eta_prediction (
    user_id,
    tax_year,
    status,
    eta_days,
    estimated_available_at,
    model_name,
    model_version,
    features,
    created_at
)
SELECT
    pb.user_id,
    pb.tax_year,
    pb.status,
    pb.eta_days,
    pb.occurred_at + make_interval(days => pb.eta_days),
    sm.model_name,
    sm.model_version,
    jsonb_build_object(
        'status', pb.status,
        'filing_state', COALESCE(pb.filing_state, 'NA'),
        'expected_amount', pb.expected_amount,
        'dow', EXTRACT(DOW FROM pb.occurred_at)::int,
        'month', EXTRACT(MONTH FROM pb.occurred_at)::int
    ),
    pb.occurred_at + interval '3 minutes'
FROM prediction_base pb
CROSS JOIN seed_model sm
ORDER BY pb.user_id, pb.tax_year, pb.status;

COMMIT;