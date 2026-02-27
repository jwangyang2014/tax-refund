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
