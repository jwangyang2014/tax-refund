# =============================================================================
# observability.tf
# Structured-log ingestion, Log-based metrics, and alerting for the
# TurboTax Refund Status backend.
#
# What this file adds on top of the existing main.tf:
#   1. Cloud Logging log bucket + log sink  – dedicated retention-managed bucket
#      so refund-status logs are queryable in Log Explorer without noise.
#   2. Log-based metrics  – snake_case events (rate_limited, jwt_invalid, etc.)
#      converted to Cloud Monitoring time-series for charting and alerting.
#   3. Alerting policies  – error-rate, rate-limit spike, JWT failure alerts
#      wired to an e-mail channel (swap for PagerDuty / Slack as needed).
#   4. IAM – roles/cloudtrace.agent so the Cloud Run SA can write OTel spans
#      to Cloud Trace (pairs with OTEL_EXPORTER_OTLP_ENDPOINT in main.tf).
#
# Prerequisites (already in main.tf, listed here for clarity):
#   - google_service_account.run_sa
#   - google_project_iam_member.run_sa_log_writer   (roles/logging.logWriter)
#   - google_project_iam_member.run_sa_metric_writer (roles/monitoring.metricWriter)
# =============================================================================

# ---------------------------------------------------------------------------
# 0. Locals used across this file
# ---------------------------------------------------------------------------
locals {
  # Filter that scopes all log sinks / metrics to Cloud Run logs produced by
  # our service account only. Adjust if you add more services later.
  log_filter_prefix = "resource.type=\"cloud_run_revision\" AND logName=~\"projects/${var.project_id}/logs/\""
}

# ---------------------------------------------------------------------------
# 1. Dedicated Cloud Logging bucket
#    Keeps refund-status logs separate from the default _Default bucket and
#    lets you set an independent retention window.
# ---------------------------------------------------------------------------
resource "google_logging_project_bucket_config" "app_logs" {
  project        = var.project_id
  location       = "global"
  bucket_id      = "${local.prefix}-app-logs"
  retention_days = var.log_retention_days

  description = "Structured JSON logs for ${local.prefix} Cloud Run backend"
}

# ---------------------------------------------------------------------------
# 2. Log sink – routes matching logs into the dedicated bucket
# ---------------------------------------------------------------------------
resource "google_logging_project_sink" "app_sink" {
  name        = "${local.prefix}-app-sink"
  destination = "logging.googleapis.com/${google_logging_project_bucket_config.app_logs.id}"

  # Only forward logs from our Cloud Run service SA so noise is minimal
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND labels."goog-managed-by"!="cloudfunctions"
  EOT

  # Let Terraform manage the sink's writer SA permissions below
  unique_writer_identity = true
}

# Grant the sink's auto-generated writer SA permission to write into the bucket
resource "google_project_iam_member" "sink_log_writer" {
  project = var.project_id
  role    = "roles/logging.bucketWriter"
  member  = google_logging_project_sink.app_sink.writer_identity
}

# ---------------------------------------------------------------------------
# 3. Log-based metrics
#    Each metric corresponds to a snake_case event already logged by the app.
#    These surface in Cloud Monitoring under the
#    "logging.googleapis.com/user/<metric_name>" namespace.
# ---------------------------------------------------------------------------

# 3a. Application error rate (ERROR-level logs from our namespace)
resource "google_logging_metric" "app_errors" {
  name   = "${local.prefix}-app-errors"
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND severity="ERROR"
    AND jsonPayload.logger=~"com\\.intuit\\.taxrefund"
  EOT

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    labels {
      key         = "logger"
      value_type  = "STRING"
      description = "Logger class that emitted the error"
    }
  }

  label_extractors = {
    "logger" = "EXTRACT(jsonPayload.logger)"
  }
}

# 3b. Rate-limit hits (rate_limited event)
resource "google_logging_metric" "rate_limit_hits" {
  name   = "${local.prefix}-rate-limit-hits"
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND jsonPayload.message=~"rate_limited"
  EOT

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    labels {
      key         = "path"
      value_type  = "STRING"
      description = "API path that was rate-limited"
    }
  }

  label_extractors = {
    "path" = "EXTRACT(jsonPayload.mdc.requestPath)"
  }
}

# 3c. JWT validation failures
resource "google_logging_metric" "jwt_failures" {
  name   = "${local.prefix}-jwt-failures"
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND jsonPayload.message=~"jwt_invalid|jwt_parse_failed"
  EOT

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
  }
}

# 3d. ML ETA predictions (ml_predict_ok events) – useful for throughput SLO
resource "google_logging_metric" "ml_predictions" {
  name   = "${local.prefix}-ml-predictions"
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND jsonPayload.message=~"ml_predict_ok"
  EOT

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    labels {
      key         = "modelName"
      value_type  = "STRING"
      description = "ML model that served the prediction"
    }
  }

  label_extractors = {
    "modelName" = "EXTRACT(jsonPayload.mdc.modelName)"
  }
}

# 3e. Refund status changes (refund_status_changed events)
resource "google_logging_metric" "refund_status_changes" {
  name   = "${local.prefix}-refund-status-changes"
  filter = <<-EOT
    ${local.log_filter_prefix}
    AND jsonPayload.message=~"refund_status_changed"
  EOT

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    unit        = "1"
    labels {
      key         = "newStatus"
      value_type  = "STRING"
      description = "New refund status after the change"
    }
  }

  label_extractors = {
    "newStatus" = "EXTRACT(jsonPayload.mdc.newStatus)"
  }
}

# ---------------------------------------------------------------------------
# 4. Notification channel (e-mail) – replace / augment with PagerDuty/Slack
# ---------------------------------------------------------------------------
resource "google_monitoring_notification_channel" "ops_email" {
  display_name = "${local.prefix} ops alerts"
  type         = "email"

  labels = {
    email_address = var.ops_alert_email
  }
}

# ---------------------------------------------------------------------------
# 5. Alerting policies
# ---------------------------------------------------------------------------

# 5a. Elevated error rate – fires when >10 ERROR logs per minute for 5 min
resource "google_monitoring_alert_policy" "high_error_rate" {
  display_name = "${local.prefix}: elevated application error rate"
  combiner     = "OR"

  conditions {
    display_name = "Error log count > 10/min for 5 min"

    condition_threshold {
      filter = <<-EOT
        metric.type="logging.googleapis.com/user/${google_logging_metric.app_errors.name}"
        AND resource.type="cloud_run_revision"
      EOT

      comparison      = "COMPARISON_GT"
      threshold_value = 10
      duration        = "300s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.ops_email.id]
  alert_strategy {
    auto_close = "1800s"
  }
}

# 5b. Rate-limit spike – fires when >50 hits per minute (possible abuse / bug)
resource "google_monitoring_alert_policy" "rate_limit_spike" {
  display_name = "${local.prefix}: rate-limit spike"
  combiner     = "OR"

  conditions {
    display_name = "Rate-limit hit count > 50/min for 3 min"

    condition_threshold {
      filter = <<-EOT
        metric.type="logging.googleapis.com/user/${google_logging_metric.rate_limit_hits.name}"
        AND resource.type="cloud_run_revision"
      EOT

      comparison      = "COMPARISON_GT"
      threshold_value = 50
      duration        = "180s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.ops_email.id]
  alert_strategy {
    auto_close = "1800s"
  }
}

# 5c. JWT failure spike – unexpected auth failures may indicate an attack
resource "google_monitoring_alert_policy" "jwt_failure_spike" {
  display_name = "${local.prefix}: JWT failure spike"
  combiner     = "OR"

  conditions {
    display_name = "JWT failure count > 20/min for 3 min"

    condition_threshold {
      filter = <<-EOT
        metric.type="logging.googleapis.com/user/${google_logging_metric.jwt_failures.name}"
        AND resource.type="cloud_run_revision"
      EOT

      comparison      = "COMPARISON_GT"
      threshold_value = 20
      duration        = "180s"

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.ops_email.id]
  alert_strategy {
    auto_close = "1800s"
  }
}

# ---------------------------------------------------------------------------
# 7. (Optional) Cloud Trace – grant run SA permission to write traces
#    Needed when you add the OpenTelemetry SDK + OTLP exporter and point it
#    at Cloud Trace (https://cloud.google.com/trace/docs/setup/java-ot).
# ---------------------------------------------------------------------------
resource "google_project_iam_member" "run_sa_trace_agent" {
  project = var.project_id
  role    = "roles/cloudtrace.agent"
  member  = "serviceAccount:${google_service_account.run_sa.email}"
}

# ---------------------------------------------------------------------------
# Variables for this file are declared in variables.tf:
#   - log_retention_days
#   - ops_alert_email
#   - tracing_sample_probability
# ---------------------------------------------------------------------------
