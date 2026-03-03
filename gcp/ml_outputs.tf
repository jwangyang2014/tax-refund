# =============================================================================
# ml_outputs.tf  –  Outputs for the ML service.
# =============================================================================

output "ml_service_url" {
  description = "Internal Cloud Run URL for the ML ETA prediction service (used by backend as ML_BASE_URL)"
  value       = google_cloud_run_v2_service.ml.uri
}

output "ml_artifact_image" {
  description = "Artifact Registry image URL deployed for the ML service"
  value       = local.ml_image
}
