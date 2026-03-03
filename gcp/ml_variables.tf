# =============================================================================
# ml_variables.tf  –  Variables for the ML ETA Cloud Run service.
# Add to your .tfvars files as needed (defaults are safe for staging).
# =============================================================================

variable "ml_artifact_image_tag" {
  type        = string
  description = "Docker image tag for the ML service. Defaults to latest; override with git SHA in CI."
  default     = "latest"
}

variable "ml_min_instances" {
  type        = number
  description = "Minimum Cloud Run instances for the ML service. 0 = scale to zero (staging)."
  default     = 0
}

variable "ml_max_instances" {
  type        = number
  description = "Maximum Cloud Run instances for the ML service."
  default     = 3
}

variable "ml_cpu" {
  type        = string
  description = "CPU allocation for the ML Cloud Run container (e.g. '1', '2')."
  default     = "1"
}

variable "ml_memory" {
  type        = string
  description = "Memory for the ML Cloud Run container. Model load needs at least 512Mi."
  default     = "1Gi"
}

variable "ml_cpu_idle" {
  type        = bool
  description = "Throttle CPU when not processing a request (saves cost). Set false in prod for lower latency."
  default     = true
}
