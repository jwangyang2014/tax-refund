# ============================================================
# Production environment variable overrides
# Usage: terraform apply -var-file=terraform.prod.tfvars
# ============================================================

# -- GCP Project --
# MANUAL STEP: Replace with your actual production GCP project ID.
# Create the project first: gcloud projects create taxrefund-prod --name="TaxRefund Production"
project_id = "taxrefund-prod"

# -- Environment tag (used as name prefix for all resources) --
environment = "prod"

# -- Service identity --
service_name  = "taxrefund-backend"
artifact_repo = "taxrefund"

# -- Regions --
# Multi-region for production; traffic is routed to nearest healthy backend.
primary_region  = "us-central1"
backend_regions = ["us-central1", "us-east1"]

# Read replicas in a second region for read-heavy queries.
# NOTE: The application must explicitly route read-only traffic to replica endpoints.
db_read_replica_regions = ["us-east1"]

# -- Scaling --
min_instances = 1  # keep warm in prod
max_instances = 20
concurrency   = 80
cpu           = "2"
memory        = "1Gi"

# -- Database (production-grade tier with HA) --
db_tier         = "db-custom-2-7680"
db_disk_size_gb = 50
db_name         = "taxrefund"
db_user         = "appuser"

# -- Redis --
redis_memory_gb = 2
redis_tier      = "STANDARD_HA"  # HA with automatic failover

# -- Secrets (these Secret Manager IDs must exist in the prod project)
# MANUAL STEP: Create each secret before first terraform apply:
#   gcloud secrets create taxrefund-jwt-secret --project=taxrefund-prod
#   gcloud secrets versions add taxrefund-jwt-secret --data-file=- --project=taxrefund-prod <<< "your-jwt-secret"
#   (repeat for db-password and openai-api-key)
sm_jwt_secret_id  = "taxrefund-jwt-secret"
sm_db_password_id = "taxrefund-db-password"
sm_openai_key_id  = "taxrefund-openai-api-key"

# -- AI --
ai_provider = "openai"  # real LLM in prod

# -- Frontend --
frontend_bucket_location       = "US"
frontend_cache_max_age_seconds = 3600

# -- ML Service --
# Keep 1 warm instance in prod to avoid cold-start ETA prediction latency.
ml_artifact_image_tag = "latest"   # CI/CD overrides this with git SHA
ml_min_instances      = 1
ml_max_instances      = 5
ml_cpu                = "2"        # more CPU for faster sklearn inference under load
ml_memory             = "2Gi"
ml_cpu_idle           = false      # keep CPU allocated to avoid throttle on burst

# -- Load balancer domains --
# MANUAL STEP: Set to your production domains before applying.
# After apply, point DNS A/AAAA records for these domains to the load_balancer_ip output.
# Managed SSL certificate provisioning can take 10–60 minutes after DNS propagates.
lb_domains = ["app.yourdomain.com", "www.yourdomain.com"]

enable_http_redirect = true  # redirect HTTP → HTTPS

# -- Networking --
serverless_connector_cidr            = "10.10.0.0/20"
private_service_range_prefix_length  = 16

# -- Safety --
# Prevents accidental destroy of Cloud SQL and Redis in production.
deletion_protection = true

# -- Observability --
# 30 days log retention in prod (default). Increase if compliance requires longer.
log_retention_days = 30
# MANUAL STEP: Replace with your real on-call email or PagerDuty/Slack email integration.
ops_alert_email = "ops-alerts@example.com"
# Sample 10% of traces in prod to control Cloud Trace costs.
tracing_sample_probability = "0.1"

# -- Observability --
alert_email        = "yang@example.com"  # MANUAL STEP: replace
log_retention_days = 365
