locals {
  prefix          = "${var.environment}-${var.service_name}"
  backend_regions = distinct(var.backend_regions)
  image           = "${var.primary_region}-docker.pkg.dev/${var.project_id}/${var.artifact_repo}/${var.service_name}:${var.artifact_image_tag}"
}

resource "random_id" "frontend_bucket_suffix" {
  byte_length = 4
}

resource "google_project_service" "services" {
  for_each = toset([
    "artifactregistry.googleapis.com",
    "cloudsql.googleapis.com",
    "compute.googleapis.com",
    "cloudtrace.googleapis.com",
    "iam.googleapis.com",
    "logging.googleapis.com",
    "monitoring.googleapis.com",
    "redis.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "servicenetworking.googleapis.com",
    "sqladmin.googleapis.com",
    "storage.googleapis.com",
    "vpcaccess.googleapis.com",
  ])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "repo" {
  depends_on    = [google_project_service.services]
  location      = var.primary_region
  repository_id = var.artifact_repo
  format        = "DOCKER"
}

resource "google_compute_network" "vpc" {
  depends_on              = [google_project_service.services]
  name                    = "${local.prefix}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "serverless" {
  for_each      = toset(local.backend_regions)
  name          = "${local.prefix}-${each.key}-svpc"
  region        = each.key
  network       = google_compute_network.vpc.id
  ip_cidr_range = cidrsubnet(var.serverless_connector_cidr, 8, index(local.backend_regions, each.key))
}

resource "google_vpc_access_connector" "connector" {
  for_each = google_compute_subnetwork.serverless

  name   = "${local.prefix}-${each.key}-conn"
  region = each.key

  subnet {
    name = each.value.name
  }

  min_instances = 2
  max_instances = 3

  depends_on = [google_project_service.services]
}

resource "google_compute_global_address" "private_service_range" {
  name          = "${local.prefix}-private-svc-range"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = var.private_service_range_prefix_length
  network       = google_compute_network.vpc.id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_service_range.name]

  depends_on = [google_project_service.services]
}

resource "google_redis_instance" "redis" {
  name               = "${local.prefix}-redis"
  region             = var.primary_region
  tier               = var.redis_tier
  memory_size_gb     = var.redis_memory_gb
  authorized_network = google_compute_network.vpc.id
  connect_mode       = "PRIVATE_SERVICE_ACCESS"
  redis_version      = "REDIS_7_0"
  display_name       = "${local.prefix} redis"

  depends_on = [google_project_service.services]
}

resource "google_sql_database_instance" "primary" {
  name                = "${local.prefix}-pg"
  region              = var.primary_region
  database_version    = var.db_version
  deletion_protection = var.deletion_protection

  settings {
    tier              = var.db_tier
    availability_type = "REGIONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.db_disk_size_gb
    disk_autoresize   = true

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7

      backup_retention_settings {
        retained_backups = 7
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = google_compute_network.vpc.id
      enable_private_path_for_google_cloud_services = true
    }

    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = true
    }

    maintenance_window {
      day  = 7
      hour = 3
    }
  }

  depends_on = [google_service_networking_connection.private_vpc_connection]
}

resource "google_sql_database" "db" {
  name     = var.db_name
  instance = google_sql_database_instance.primary.name
}

data "google_secret_manager_secret_version" "db_password" {
  secret  = var.sm_db_password_id
  version = "latest"
}

resource "google_sql_user" "app" {
  name     = var.db_user
  instance = google_sql_database_instance.primary.name
  password = data.google_secret_manager_secret_version.db_password.secret_data
}

resource "google_sql_database_instance" "read_replica" {
  for_each = toset(var.db_read_replica_regions)

  name                 = "${local.prefix}-pg-rr-${replace(each.key, "/", "-")}"
  region               = each.key
  database_version     = var.db_version
  master_instance_name = google_sql_database_instance.primary.name
  deletion_protection  = var.deletion_protection

  replica_configuration {}

  settings {
    tier              = var.db_tier
    availability_type = "REGIONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.db_disk_size_gb
    disk_autoresize   = true

    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = google_compute_network.vpc.id
      enable_private_path_for_google_cloud_services = true
    }

    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = true
    }
  }

  depends_on = [google_sql_database_instance.primary]
}

resource "google_service_account" "run_sa" {
  account_id   = substr(replace("${local.prefix}-run-sa", "_", "-"), 0, 30)
  display_name = "${local.prefix} Cloud Run runtime"
}

resource "google_project_iam_member" "run_sa_secret_access" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.run_sa.email}"
}

resource "google_project_iam_member" "run_sa_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.run_sa.email}"
}

resource "google_project_iam_member" "run_sa_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.run_sa.email}"
}

resource "google_cloud_run_v2_service" "backend" {
  for_each = toset(local.backend_regions)

  name     = local.prefix
  location = each.key
  ingress  = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

  template {
    service_account = google_service_account.run_sa.email

    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }

    vpc_access {
      connector = google_vpc_access_connector.connector[each.key].id
      egress    = "PRIVATE_RANGES_ONLY"
    }

    max_instance_request_concurrency = var.concurrency
    timeout                          = "60s"

    containers {
      image = local.image

      ports {
        container_port = var.container_port
      }

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
      }

      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
          port = var.container_port
        }
        initial_delay_seconds = 20
        period_seconds        = 10
      }

      startup_probe {
        http_get {
          path = "/actuator/health/readiness"
          port = var.container_port
        }
        initial_delay_seconds = 5
        period_seconds        = 5
        failure_threshold     = 30
      }

      env {
        name  = "APP_SECURITY_JWT_ISSUER"
        value = "refund-status"
      }

      env {
        name = "APP_SECURITY_JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = var.sm_jwt_secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "REDIS_HOST"
        value = google_redis_instance.redis.host
      }

      env {
        name  = "REDIS_PORT"
        value = tostring(google_redis_instance.redis.port)
      }

      env {
        name  = "POSTGRES_DB_USERNAME"
        value = var.db_user
      }

      env {
        name = "POSTGRES_DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.sm_db_password_id
            version = "latest"
          }
        }
      }

      env {
        name  = "POSTGRES_DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.primary.private_ip_address}:5432/${var.db_name}"
      }

      env {
        name  = "AI_PROVIDER"
        value = var.ai_provider
      }

      env {
        name = "OPENAI_API_KEY"
        value_source {
          secret_key_ref {
            secret  = var.sm_openai_key_id
            version = "latest"
          }
        }
      }

      env {
        name  = "APP_RATELIMIT_ENABLED"
        value = "true"
      }

      # -----------------------------------------------------------------------
      # Observability env vars – consumed by application.yml and log4j2 config
      # APP_SERVICE_NAME : used as the "service" field in structured JSON logs
      # APP_ENV          : used as the "env" field in structured JSON logs
      # TRACING_SAMPLE_PROBABILITY : fraction of requests sampled for Cloud Trace
      # -----------------------------------------------------------------------
      env {
        name  = "APP_SERVICE_NAME"
        value = local.prefix
      }

      env {
        name  = "APP_ENV"
        value = var.environment
      }

      env {
        name  = "TRACING_SAMPLE_PROBABILITY"
        value = var.tracing_sample_probability
      }

      env {
        # OTLP HTTP endpoint – Cloud Run's service account already has
        # roles/cloudtrace.agent (observability.tf) so no extra auth needed.
        # Override via TF_VAR_otlp_endpoint for Jaeger / staging collectors.
        name  = "OTEL_EXPORTER_OTLP_ENDPOINT"
        value = var.otlp_endpoint
      }      
    }
  }

  depends_on = [
    google_project_iam_member.run_sa_secret_access,
    google_project_iam_member.run_sa_log_writer,
    google_project_iam_member.run_sa_metric_writer,
    google_project_service.services,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  for_each = google_cloud_run_v2_service.backend

  project  = var.project_id
  location = each.value.location
  name     = each.value.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_compute_region_network_endpoint_group" "serverless_neg" {
  for_each = google_cloud_run_v2_service.backend

  name                  = "${local.prefix}-${each.key}-neg"
  network_endpoint_type = "SERVERLESS"
  region                = each.key

  cloud_run {
    service = each.value.name
  }
}

resource "google_storage_bucket" "frontend" {
  name                        = var.frontend_bucket_name != "" ? var.frontend_bucket_name : "${local.prefix}-fe-${random_id.frontend_bucket_suffix.hex}"
  location                    = var.frontend_bucket_location
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  force_destroy               = false
  public_access_prevention    = "inherited"

  website {
    main_page_suffix = "index.html"
    not_found_page   = "index.html"
  }

  cors {
    origin          = length(var.lb_domains) > 0 ? [for d in var.lb_domains : "https://${d}"] : ["*"]
    method          = ["GET", "HEAD", "OPTIONS"]
    response_header = ["*"]
    max_age_seconds = 3600
  }
}

resource "google_storage_bucket_iam_member" "frontend_public_read" {
  bucket = google_storage_bucket.frontend.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

resource "google_compute_backend_bucket" "frontend" {
  name        = "${local.prefix}-frontend-bucket"
  bucket_name = google_storage_bucket.frontend.name
  enable_cdn  = true

  cdn_policy {
    cache_mode        = "CACHE_ALL_STATIC"
    client_ttl        = var.frontend_cache_max_age_seconds
    default_ttl       = var.frontend_cache_max_age_seconds
    max_ttl           = 86400
    negative_caching  = true
    serve_while_stale = 86400
  }
}

resource "google_compute_backend_service" "api" {
  name                  = "${local.prefix}-api-bs"
  protocol              = "HTTP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  timeout_sec           = 60
  enable_cdn            = false

  log_config {
    enable      = true
    sample_rate = 1.0
  }

  dynamic "backend" {
    for_each = google_compute_region_network_endpoint_group.serverless_neg
    content {
      group = backend.value.id
    }
  }
}

resource "google_compute_global_address" "lb_ip" {
  name = "${local.prefix}-lb-ip"
}

resource "google_compute_managed_ssl_certificate" "cert" {
  count = length(var.lb_domains) > 0 ? 1 : 0

  name = "${local.prefix}-managed-cert"

  managed {
    domains = var.lb_domains
  }
}

resource "google_compute_url_map" "https" {
  name            = "${local.prefix}-https-map"
  default_service = google_compute_backend_bucket.frontend.id

  host_rule {
    hosts        = length(var.lb_domains) > 0 ? var.lb_domains : ["*"]
    path_matcher = "routes"
  }

  path_matcher {
    name            = "routes"
    default_service = google_compute_backend_bucket.frontend.id

    path_rule {
      paths   = ["/api", "/api/*", "/actuator/*"]
      service = google_compute_backend_service.api.id
    }
  }
}

resource "google_compute_target_https_proxy" "https" {
  name    = "${local.prefix}-https-proxy"
  url_map = google_compute_url_map.https.id

  ssl_certificates = length(var.lb_domains) > 0 ? [google_compute_managed_ssl_certificate.cert[0].id] : []
}

resource "google_compute_global_forwarding_rule" "https" {
  name                  = "${local.prefix}-https-fr"
  ip_protocol           = "TCP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  port_range            = "443"
  target                = google_compute_target_https_proxy.https.id
  ip_address            = google_compute_global_address.lb_ip.id
}

resource "google_compute_url_map" "http_redirect" {
  count = var.enable_http_redirect ? 1 : 0

  name = "${local.prefix}-http-redirect"

  default_url_redirect {
    https_redirect         = true
    strip_query            = false
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
  }
}

resource "google_compute_target_http_proxy" "http_redirect" {
  count   = var.enable_http_redirect ? 1 : 0
  name    = "${local.prefix}-http-proxy"
  url_map = google_compute_url_map.http_redirect[0].id
}

resource "google_compute_global_forwarding_rule" "http_redirect" {
  count = var.enable_http_redirect ? 1 : 0

  name                  = "${local.prefix}-http-fr"
  ip_protocol           = "TCP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  port_range            = "80"
  target                = google_compute_target_http_proxy.http_redirect[0].id
  ip_address            = google_compute_global_address.lb_ip.id
}
