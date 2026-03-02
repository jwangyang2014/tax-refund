variable "project_id" {
  type        = string
  description = "Google Cloud project ID."
}

variable "primary_region" {
  type        = string
  description = "Primary region for Cloud SQL and one backend Cloud Run deployment."
  default     = "us-central1"
}

variable "backend_regions" {
  type        = list(string)
  description = "Regions where the backend Cloud Run service is deployed. Include the primary region."
  default     = ["us-central1", "us-east1"]
}

variable "environment" {
  type        = string
  description = "Environment name, e.g. dev/staging/prod."
  default     = "prod"
}

variable "service_name" {
  type        = string
  description = "Base name for the backend service."
  default     = "taxrefund-backend"
}

variable "artifact_repo" {
  type        = string
  description = "Artifact Registry repository for container images."
  default     = "taxrefund"
}

variable "container_port" {
  type        = number
  default     = 8080
}

variable "min_instances" {
  type        = number
  default     = 1
}

variable "max_instances" {
  type        = number
  default     = 20
}

variable "concurrency" {
  type        = number
  default     = 80
}

variable "cpu" {
  type        = string
  default     = "1"
}

variable "memory" {
  type        = string
  default     = "1Gi"
}

variable "artifact_image_tag" {
  type        = string
  description = "Immutable image tag to deploy. Avoid using latest in production."
  default     = "latest"
}

variable "db_name" {
  type        = string
  default     = "taxrefund"
}

variable "db_user" {
  type        = string
  default     = "appuser"
}

variable "db_tier" {
  type        = string
  default     = "db-custom-2-7680"
}

variable "db_disk_size_gb" {
  type        = number
  default     = 50
}

variable "db_version" {
  type        = string
  default     = "POSTGRES_16"
}

variable "db_read_replica_regions" {
  type        = list(string)
  description = "Regions for Cloud SQL read replicas."
  default     = ["us-east1"]
}

variable "redis_memory_gb" {
  type        = number
  default     = 1
}

variable "redis_tier" {
  type        = string
  default     = "STANDARD_HA"
}

variable "sm_jwt_secret_id" {
  type        = string
  default     = "taxrefund-jwt-secret"
}

variable "sm_db_password_id" {
  type        = string
  default     = "taxrefund-db-password"
}

variable "sm_openai_key_id" {
  type        = string
  default     = "taxrefund-openai-api-key"
}

variable "ai_provider" {
  type        = string
  default     = "mock"
}

variable "frontend_bucket_name" {
  type        = string
  description = "Optional explicit bucket name for the frontend. Leave empty to auto-generate."
  default     = ""
}

variable "frontend_bucket_location" {
  type        = string
  description = "Use a multi-region location for frontend assets."
  default     = "US"
}

variable "frontend_cache_max_age_seconds" {
  type        = number
  default     = 3600
}

variable "lb_domains" {
  type        = list(string)
  description = "Hostnames attached to the managed certificate and load balancer."
  default     = []
}

variable "enable_http_redirect" {
  type        = bool
  default     = true
}

variable "serverless_connector_cidr" {
  type        = string
  description = "Base CIDR block used to carve out /28 subnets for regional Serverless VPC Access connectors."
  default     = "10.10.0.0/20"
}

variable "private_service_range_prefix_length" {
  type        = number
  default     = 16
}

variable "deletion_protection" {
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Observability variables (consumed by observability.tf and main.tf)
# ---------------------------------------------------------------------------

variable "log_retention_days" {
  type        = number
  description = "Retention window in days for the dedicated Cloud Logging bucket."
  default     = 30
}

variable "ops_alert_email" {
  type        = string
  description = "Email address for operational alerts. Replace with a PagerDuty or Slack webhook channel in production."
  default     = "ops-alerts@example.com"
}

variable "tracing_sample_probability" {
  type        = string
  description = "Fraction of requests sampled for distributed tracing (0.0–1.0). Use 1.0 in dev, 0.1 in prod."
  default     = "0.1"
}

variable "otlp_endpoint" {
  type        = string
  description = "OTLP HTTP endpoint for OTel span export. Cloud Trace in GCP, or a Jaeger host in staging."
  default     = "https://cloudtrace.googleapis.com/v1/traces"
}

# ---------------------------------------------------------------------------
# Observability variables (originally declared in observability.tf, moved
# here so all variable declarations are in one place)
# ---------------------------------------------------------------------------

variable "log_retention_days" {
  type        = number
  description = "Retention window (days) for the dedicated Cloud Logging bucket."
  default     = 30
}

variable "ops_alert_email" {
  type        = string
  description = "E-mail address for operational alerts. Override with a PagerDuty/Slack integration channel."
  default     = "ops-alerts@example.com"
}

variable "tracing_sample_probability" {
  type        = string
  description = "Fraction of requests to sample for distributed tracing via Cloud Trace (0.0 – 1.0). Use '1.0' in staging, '0.1' in prod."
  default     = "0.1"
}
