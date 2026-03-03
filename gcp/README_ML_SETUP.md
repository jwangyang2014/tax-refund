# ML Service – GCP Setup & Deployment Guide

## What changed

The ML ETA prediction service (`ml/app.py`) was running locally via `docker-compose`
but was **not deployed to GCP**. The following files were added/updated to fix this:

| File | Change |
|------|--------|
| `gcp/ml.tf` | **NEW** – Cloud Run service + IAM for ML |
| `gcp/ml_variables.tf` | **NEW** – Variables for ML sizing / image tag |
| `gcp/ml_outputs.tf` | **NEW** – Outputs: `ml_service_url`, `ml_artifact_image` |
| `gcp/main.tf` | **PATCH** – Add `ML_BASE_URL` env var to backend Cloud Run |
| `gcp/terraform_staging.tfvars` | **PATCH** – Add ML sizing block |
| `gcp/terraform_prod.tfvars` | **PATCH** – Add ML sizing block |
| `.github/workflows/ml-cd.yml` | **NEW** – CI/CD pipeline for ML image |
| `_env` | **UPDATED** – Added `ML_BASE_URL=http://ml:8000` |

---

## One-time GCP setup (before first deploy)

No new secrets are needed — the ML service reuses `taxrefund-db-password`
(already in Secret Manager) to connect to Cloud SQL for training data.

```bash
# Verify the secret exists in each project
gcloud secrets describe taxrefund-db-password --project=taxrefund-staging
gcloud secrets describe taxrefund-db-password --project=taxrefund-prod
```

---

## Building and pushing the ML image manually (first deploy)

```bash
# Staging
ML_SERVICE="staging-taxrefund-backend-ml"
ML_IMAGE="us-central1-docker.pkg.dev/taxrefund-staging/taxrefund/${ML_SERVICE}:initial"

docker build -t "${ML_IMAGE}" ml/
docker push "${ML_IMAGE}"

# Apply with the new image tag
cd gcp
terraform apply -var-file=terraform.staging.tfvars \
  -var="ml_artifact_image_tag=initial"

# Verify the ML service is healthy
gcloud run services describe ${ML_SERVICE} \
  --region=us-central1 --project=taxrefund-staging

# Test the /health endpoint (from within VPC or using gcloud run proxy)
gcloud run services proxy ${ML_SERVICE} --region=us-central1 --project=taxrefund-staging &
curl http://localhost:8080/health
```

---

## Training the model after first deploy

The ML service starts without a trained model (`/models/eta_model.joblib` absent).
Trigger training manually after deploy once the DB has enough refund_status_event rows:

```bash
# Via gcloud run proxy (or from a Cloud Run Job / Cloud Scheduler in prod)
curl -X POST http://localhost:8080/train
# Expected response: {"status": "trained", "rows": N, "modelVersion": "..."}
```

Production recommendation: set up a **Cloud Scheduler** job that calls `POST /train`
weekly so the model stays current with IRS processing patterns.

---

## Architecture after fix

```
Spring Boot backend (Cloud Run)
  │
  │  ML_BASE_URL = https://<ml-service>.run.app  (injected by Terraform)
  │
  └──► ML Service (Cloud Run, internal-only ingress)
         │
         ├── POST /predict   ← called by MlEtaClient per refund status update
         ├── GET  /health    ← liveness / startup probe
         ├── GET  /model/info
         └── POST /train     ← triggered manually or by Cloud Scheduler
               │
               └──► Cloud SQL (reads refund_status_event for training data)
```

---

## GitHub Actions workflow variables needed

Add to both `staging` and `prod` GitHub Environments (Settings → Environments):

| Variable | Example value |
|----------|--------------|
| `ENVIRONMENT` | `staging` / `prod` |
| `SERVICE_NAME` | `taxrefund-backend` |

These already exist if you followed the original README. The ML service name
is derived as `{ENVIRONMENT}-{SERVICE_NAME}-ml` automatically.
