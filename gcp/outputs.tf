output "frontend_bucket_name" {
  description = "GCS bucket serving the frontend SPA"
  value       = google_storage_bucket.frontend.name
}

output "url_map_name" {
  description = "Global URL map name used by the external Application Load Balancer"
  value       = google_compute_url_map.https.name
}

output "load_balancer_ip" {
  description = "Global public IP address of the external Application Load Balancer"
  value       = google_compute_global_address.lb_ip.address
}

output "artifact_image" {
  description = "Artifact Registry image URL currently configured for Cloud Run"
  value       = local.image
}

output "backend_regions" {
  description = "Cloud Run backend regions"
  value       = local.backend_regions
}