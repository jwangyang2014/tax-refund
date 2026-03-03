# =============================================================================
# ml.tf  –  Cloud Run service for the Python / FastAPI ML ETA prediction service.
#
# What this adds:
#   1. google_cloud_run_v2_service.ml  – FastAPI ETA server (primary region;
#      stateless and latency-tolerant, replicate like backend if needed).
#   2. IAM  – backend SA can invoke ML service internally (no public URL).
#   3. Locals so main.tf injects ML_BASE_URL into the backend service automatically.
#
# Dependencies already in main.tf:
#   - google_service_account.run_sa
#   - google_vpc_access_connector.connector[var.primary_region]
#   - google_sql_database_instance.primary
#   - var.sm_db_password_id, var.db_user, var.db_name
# =============================================================================

locals {
  ml_service_name = "${local.prefix}-ml"
  ml_image = (
    var.ml_artifact_image_tag != "" ?
    "${var.primary_region}-docker.pkg.dev/${var.project_id}/${var.artifact_repo}/${local.ml_service_name}:${var.ml_artifact_image_tag}" :
    "${var.primary_region}-docker.pkg.dev/${var.project_id}/${var.artifact_repo}/${local.ml_service_name}:latest"
  )
}

# ---------------------------------------------------------------------------
# 1. ML Cloud Run service (primary region only – internal ingress)
# ---------------------------------------------------------------------------
resource "google_cloud_run_v2_service" "ml" {
  name     = local.ml_service_name
  location = var.primary_region

  # Internal-only: reachable from the backend via VPC. No public exposure.
  ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"

  template {
    service_account = google_service_account.run_sa.email

    scaling {
      min_instance_count = var.ml_min_instances
      max_instance_count = var.ml_max_instances
    }

    vpc_access {
      connector = google_vpc_access_connector.connector[var.primary_region].id
      egress    = "PRIVATE_RANGES_ONLY"
    }

    max_instance_request_concurrency = 20
    timeout                          = "120s"

    containers {
      image = local.ml_image

      ports {
        container_port = 8000
      }

      resources {
        limits = {
          cpu    = var.ml_cpu
          memory = var.ml_memory
        }
        # Only allocate CPU during request processing to save cost on staging.
        # Set cpu_idle = false in prod if warm-start latency matters.
        cpu_idle = var.ml_cpu_idle
      }

      # Wait until model is loaded before accepting traffic.
      startup_probe {
        http_get {
          path = "/health"
          port = 8000
        }
        initial_delay_seconds = 10
        period_seconds        = 5
        failure_threshold     = 24  # up to 2 min for cold model load
        timeout_seconds       = 5
      }

      liveness_probe {
        http_get {
          path = "/health"
          port = 8000
        }
        initial_delay_seconds = 30
        period_seconds        = 15
        timeout_seconds       = 5
      }

      # DB URL – ML service reads refund_status_event for training
      env {
        name  = "ML_DB_URL"
        value = "postgresql+psycopg2://${var.db_user}@${google_sql_database_instance.primary.private_ip_address}:5432/${var.db_name}"
      }

      # DB password from Secret Manager (same secret the backend uses)
      env {
        name = "ML_DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.sm_db_password_id
            version = "latest"
          }
        }
      }

      env {
        name  = "ML_MODEL_PATH"
        value = "/models/eta_model.joblib"
      }

      env {
        name  = "ML_MODEL_META_PATH"
        value = "/models/eta_model_meta.json"
      }

      env {
        name  = "APP_SERVICE_NAME"
        value = local.ml_service_name
      }

      env {
        name  = "APP_ENV"
        value = var.environment
      }
    }
  }

  depends_on = [
    google_project_service.services,
    google_vpc_access_connector.connector,
    google_sql_database_instance.primary,
  ]

  lifecycle {
    ignore_changes = [
      template[0].annotations,
      client,
      client_version,
    ]
  }
}

# ---------------------------------------------------------------------------
# 2. IAM – backend SA is the invoker (service-to-service OIDC, no API key)
# ---------------------------------------------------------------------------
resource "google_cloud_run_v2_service_iam_member" "ml_invoker" {
  project  = var.project_id
  location = var.primary_region
  name     = google_cloud_run_v2_service.ml.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.run_sa.email}"
}
