# ============================================================
# Staging environment variable overrides
# Usage: terraform apply -var-file=terraform.staging.tfvars
# ============================================================

# -- GCP Project --
# MANUAL STEP: Replace with your actual staging GCP project ID.
# Create the project first: gcloud projects create taxrefund-staging --name="TaxRefund Staging"
project_id = "taxrefund-staging"

# -- Environment tag (used as name prefix for all resources) --
environment = "staging"

# -- Service identity --
service_name  = "taxrefund-backend"
artifact_repo = "taxrefund"

# -- Regions --
# Single region for staging to keep costs low.
primary_region  = "us-central1"
backend_regions = ["us-central1"]

# No read replicas in staging.
db_read_replica_regions = []

# -- Scaling (smaller for staging) --
min_instances = 0  # scale to zero when idle
max_instances = 3
concurrency   = 40
cpu           = "1"
memory        = "512Mi"

# -- Database (smaller tier for staging) --
db_tier        = "db-g1-small"
db_disk_size_gb = 10
db_name        = "taxrefund"
db_user        = "appuser"

# -- Redis --
redis_memory_gb = 1
redis_tier      = "BASIC"  # no HA needed in staging

# -- Secrets (these Secret Manager IDs must exist in the staging project)
# MANUAL STEP: Create each secret before first terraform apply:
#   gcloud secrets create taxrefund-jwt-secret --project=taxrefund-staging
#   gcloud secrets versions add taxrefund-jwt-secret --data-file=- --project=taxrefund-staging <<< "your-jwt-secret"
#   (repeat for db-password and openai-api-key)
sm_jwt_secret_id  = "taxrefund-jwt-secret"
sm_db_password_id = "taxrefund-db-password"
sm_openai_key_id  = "taxrefund-openai-api-key"

# -- AI --
ai_provider = "mock"  # use mock LLM in staging to avoid OpenAI charges

# -- Frontend --
frontend_bucket_location       = "US"
frontend_cache_max_age_seconds = 60  # short TTL in staging for fast iteration

# -- Load balancer domains --
# MANUAL STEP: Set to your staging domain, e.g. ["staging.yourdomain.com"]
# Leave empty [] if you have no domain yet; HTTP only will be served.
lb_domains = []

enable_http_redirect = false  # no redirect when no domain/cert

# -- Networking --
serverless_connector_cidr            = "10.20.0.0/20"
private_service_range_prefix_length  = 16

# -- Safety --
# Allow destroy in staging without manual override.
deletion_protection = false

# -- Observability --
# Short log retention in staging to keep costs low.
log_retention_days = 7
# MANUAL STEP: Replace with a real team/personal email for alert routing.
ops_alert_email = "dev-alerts@example.com"
# Sample 100% of traces in staging so every request is visible.
tracing_sample_probability = "1.0"

# -- Observability --
alert_email        = "yourteam@yourdomain.com"  # MANUAL STEP: replace
log_retention_days = 90
