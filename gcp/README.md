# TaxRefund – GCP Infrastructure & Deployment Guide

Complete end-to-end guide for provisioning, deploying, and operating the TaxRefund application on Google Cloud Platform.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Repository Layout](#repository-layout)
3. [Prerequisites](#prerequisites)
4. [Manual One-Time Setup (Do This First)](#manual-one-time-setup-do-this-first)
   - [1. Create GCP Projects](#1-create-gcp-projects)
   - [2. Enable Billing](#2-enable-billing)
   - [3. Create Terraform Remote State Buckets](#3-create-terraform-remote-state-buckets)
   - [4. Create GCP Service Accounts for CI/CD](#4-create-gcp-service-accounts-for-cicd)
   - [5. Set Up Workload Identity Federation](#5-set-up-workload-identity-federation)
   - [6. Create Secret Manager Secrets](#6-create-secret-manager-secrets)
   - [7. Configure GitHub Environments and Variables](#7-configure-github-environments-and-variables)
   - [8. Configure Your Domains (Production)](#8-configure-your-domains-production)
5. [Terraform Variable Files](#terraform-variable-files)
6. [First Deployment (Manual Bootstrap)](#first-deployment-manual-bootstrap)
7. [Day-to-Day CI/CD Flows](#day-to-day-cicd-flows)
8. [Terraform Commands Reference](#terraform-commands-reference)
9. [Backend `.tfvars` Variables Reference](#backend-tfvars-variables-reference)
10. [Architectural Notes](#architectural-notes)
11. [Optional Future Improvements](#optional-future-improvements)

---

## Architecture Overview

```
Users
  │
  ▼
Global External Application Load Balancer  (anycast IP, Cloud CDN)
  ├─► /api/*  →  Serverless NEGs  →  Cloud Run (us-central1, us-east1)
  │                                      │
  │                               VPC Access Connector
  │                                      │
  │                            ┌─────────┴──────────┐
  │                       Cloud SQL (primary)    Memorystore Redis
  │                       Cloud SQL (read replica)
  └─► /*      →  Cloud Storage Bucket (SPA assets, public, CDN-backed)

Secrets  →  Secret Manager  →  Cloud Run (env vars at deploy time)
Images   →  Artifact Registry  →  Cloud Run
```

- **Backend**: Spring Boot on Cloud Run, multi-region, behind Global LB via Serverless NEGs
- **Frontend**: React (Vite) SPA served from GCS + Cloud CDN
- **Database**: Cloud SQL for PostgreSQL with private IP, regional HA, optional cross-region read replica
- **Cache**: Memorystore for Redis (private VPC)
- **Secrets**: Secret Manager; injected into Cloud Run as environment variables at deploy time
- **CI/CD**: GitHub Actions → Artifact Registry → Terraform apply

---

## Repository Layout

```
.
├── backend/                  # Spring Boot application
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                 # React + Vite application
│   ├── src/
│   └── package.json
├── gcp/                      # All Terraform lives here
│   ├── main.tf               # Core resource definitions
│   ├── variables.tf          # Variable declarations
│   ├── outputs.tf            # Outputs consumed by CI/CD
│   ├── terraform.staging.tfvars  # Staging overrides
│   ├── terraform.prod.tfvars     # Production overrides
│   └── backend.tf            # (you must create this – see below)
└── .github/
    └── workflows/
        ├── backend-cd.yml
        ├── frontend-cd.yml
        └── terraform.yml
```

> **Note**: The Terraform backend (remote state) is configured via `-backend-config` flags in CI/CD rather than a static `backend.tf` file. This lets staging and production share the same code with different state buckets.

---

## Prerequisites

Install these tools locally before running any commands:

| Tool | Version | Install |
|------|---------|---------|
| `gcloud` CLI | latest | https://cloud.google.com/sdk/docs/install |
| `terraform` | >= 1.6 | https://developer.hashicorp.com/terraform/install |
| `docker` | latest | https://docs.docker.com/get-docker/ |
| `java` | 17 | https://adoptium.net/ |
| `node` | 20 | https://nodejs.org/ |
| `mvn` | 3.9+ | https://maven.apache.org/ |

Authenticate gcloud locally:
```bash
gcloud auth login
gcloud auth application-default login
```

---

## Manual One-Time Setup (Do This First)

These steps are performed **once per environment** before any automated CI/CD runs.

---

### 1. Create GCP Projects

You need two projects: one for staging, one for production.

```bash
# Staging
gcloud projects create taxrefund-staging \
  --name="TaxRefund Staging" \
  --set-as-default

# Production
gcloud projects create taxrefund-prod \
  --name="TaxRefund Production"
```

> **Note**: Project IDs must be globally unique. If `taxrefund-staging` is taken, use something like `taxrefund-staging-yourname`.  
> Update `project_id` in `terraform.staging.tfvars` and `terraform.prod.tfvars` accordingly.

---

### 2. Enable Billing

Terraform enables most APIs automatically, but billing must be linked manually.

```bash
# List your billing accounts
gcloud billing accounts list

# Link staging project
gcloud billing projects link taxrefund-staging \
  --billing-account=XXXXXX-XXXXXX-XXXXXX

# Link prod project
gcloud billing projects link taxrefund-prod \
  --billing-account=XXXXXX-XXXXXX-XXXXXX
```

---

### 3. Create Terraform Remote State Buckets

Each environment needs its own GCS bucket to store Terraform state. **Create these before running `terraform init`.**

```bash
# ── Staging ──
gsutil mb -p taxrefund-staging -l us-central1 gs://taxrefund-staging-tfstate
gsutil versioning set on gs://taxrefund-staging-tfstate
# Prevent accidental deletion of state
gsutil retention set 90d gs://taxrefund-staging-tfstate

# ── Production ──
gsutil mb -p taxrefund-prod -l us-central1 gs://taxrefund-prod-tfstate
gsutil versioning set on gs://taxrefund-prod-tfstate
gsutil retention set 365d gs://taxrefund-prod-tfstate
```

---

### 4. Create GCP Service Accounts for CI/CD

Each environment needs a dedicated service account that GitHub Actions will impersonate.

```bash
# ── Staging SA ──
gcloud iam service-accounts create github-actions-sa \
  --display-name="GitHub Actions CI/CD" \
  --project=taxrefund-staging

# Grant necessary roles for staging
for ROLE in \
  roles/run.admin \
  roles/storage.admin \
  roles/artifactregistry.admin \
  roles/cloudsql.admin \
  roles/iam.serviceAccountUser \
  roles/compute.networkAdmin \
  roles/secretmanager.viewer \
  roles/vpcaccess.admin \
  roles/redis.admin \
  roles/servicenetworking.networksAdmin; do
  gcloud projects add-iam-policy-binding taxrefund-staging \
    --member="serviceAccount:github-actions-sa@taxrefund-staging.iam.gserviceaccount.com" \
    --role="$ROLE"
done

# Also needs state bucket access
gsutil iam ch serviceAccount:github-actions-sa@taxrefund-staging.iam.gserviceaccount.com:roles/storage.admin \
  gs://taxrefund-staging-tfstate

# ── Production SA ──
gcloud iam service-accounts create github-actions-sa \
  --display-name="GitHub Actions CI/CD" \
  --project=taxrefund-prod

for ROLE in \
  roles/run.admin \
  roles/storage.admin \
  roles/artifactregistry.admin \
  roles/cloudsql.admin \
  roles/iam.serviceAccountUser \
  roles/compute.networkAdmin \
  roles/secretmanager.viewer \
  roles/vpcaccess.admin \
  roles/redis.admin \
  roles/servicenetworking.networksAdmin; do
  gcloud projects add-iam-policy-binding taxrefund-prod \
    --member="serviceAccount:github-actions-sa@taxrefund-prod.iam.gserviceaccount.com" \
    --role="$ROLE"
done

gsutil iam ch serviceAccount:github-actions-sa@taxrefund-prod.iam.gserviceaccount.com:roles/storage.admin \
  gs://taxrefund-prod-tfstate
```

---

### 5. Set Up Workload Identity Federation

This allows GitHub Actions to authenticate to GCP without storing long-lived service account keys.

```bash
# ── Run for EACH project (staging and prod) ──

PROJECT=taxrefund-staging   # change to taxrefund-prod for prod
SA=github-actions-sa@${PROJECT}.iam.gserviceaccount.com
GITHUB_ORG=your-github-org          # e.g. "acme-corp"
GITHUB_REPO=your-repo-name          # e.g. "taxrefund"

# Create Workload Identity Pool
gcloud iam workload-identity-pools create github-pool \
  --project="${PROJECT}" \
  --location="global" \
  --display-name="GitHub Actions Pool"

# Create OIDC provider inside the pool
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --project="${PROJECT}" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Actions OIDC Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com"

# Allow the GitHub repo to impersonate the service account
POOL_ID=$(gcloud iam workload-identity-pools describe github-pool \
  --project="${PROJECT}" --location="global" --format="value(name)")

gcloud iam service-accounts add-iam-policy-binding "${SA}" \
  --project="${PROJECT}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/${POOL_ID}/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}"

# Print the provider resource name – you'll need this for GitHub vars
gcloud iam workload-identity-pools providers describe github-provider \
  --project="${PROJECT}" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --format="value(name)"
# Output looks like:
# projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider
```

---

### 6. Create Secret Manager Secrets

The application reads three secrets at runtime. Create them **before** the first `terraform apply`.

```bash
# ── Staging ──
gcloud secrets create taxrefund-jwt-secret     --project=taxrefund-staging
gcloud secrets create taxrefund-db-password    --project=taxrefund-staging
gcloud secrets create taxrefund-openai-api-key --project=taxrefund-staging

# Add secret values (replace with real values)
echo -n "your-staging-jwt-secret-min-32-chars" | \
  gcloud secrets versions add taxrefund-jwt-secret --data-file=- --project=taxrefund-staging

echo -n "your-staging-db-password" | \
  gcloud secrets versions add taxrefund-db-password --data-file=- --project=taxrefund-staging

echo -n "sk-..." | \
  gcloud secrets versions add taxrefund-openai-api-key --data-file=- --project=taxrefund-staging

# ── Production ──
gcloud secrets create taxrefund-jwt-secret     --project=taxrefund-prod
gcloud secrets create taxrefund-db-password    --project=taxrefund-prod
gcloud secrets create taxrefund-openai-api-key --project=taxrefund-prod

echo -n "your-prod-jwt-secret-min-32-chars" | \
  gcloud secrets versions add taxrefund-jwt-secret --data-file=- --project=taxrefund-prod

echo -n "your-prod-db-password" | \
  gcloud secrets versions add taxrefund-db-password --data-file=- --project=taxrefund-prod

echo -n "sk-..." | \
  gcloud secrets versions add taxrefund-openai-api-key --data-file=- --project=taxrefund-prod
```

---

### 7. Configure GitHub Environments and Variables

In your GitHub repository go to **Settings → Environments** and create two environments: `staging` and `prod`.

For the `prod` environment, enable **Required reviewers** and add at least one reviewer. This creates a manual approval gate before production deployments proceed.

For each environment, add the following **Variables** (not secrets – these are non-sensitive):

| Variable Name | Staging value | Production value |
|---|---|---|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/STAGING_PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider` | `projects/PROD_PROJECT_NUMBER/locations/global/...` |
| `GCP_SERVICE_ACCOUNT` | `github-actions-sa@taxrefund-staging.iam.gserviceaccount.com` | `github-actions-sa@taxrefund-prod.iam.gserviceaccount.com` |
| `GCP_PROJECT_ID` | `taxrefund-staging` | `taxrefund-prod` |
| `GCP_PRIMARY_REGION` | `us-central1` | `us-central1` |
| `ARTIFACT_REPO` | `taxrefund` | `taxrefund` |
| `SERVICE_NAME` | `taxrefund-backend` | `taxrefund-backend` |
| `TF_STATE_BUCKET` | `taxrefund-staging-tfstate` | `taxrefund-prod-tfstate` |

> **Tip**: Get `STAGING_PROJECT_NUMBER` via `gcloud projects describe taxrefund-staging --format="value(projectNumber)"`

---

### 8. Configure Your Domains (Production)

After the first production Terraform apply completes:

```bash
# Get the global load balancer IP
cd gcp
terraform init -backend-config="bucket=taxrefund-prod-tfstate" -backend-config="prefix=terraform/state"
terraform output load_balancer_ip
# Returns something like: 34.120.x.x
```

Then go to your DNS registrar and add:
```
A    app.yourdomain.com      →  34.120.x.x
A    www.yourdomain.com      →  34.120.x.x
```

Google-managed SSL certificates are provisioned automatically once DNS resolves. **Certificate provisioning can take 10–60 minutes** after DNS propagates. Check status with:

```bash
gcloud compute ssl-certificates list --project=taxrefund-prod
```

---

## Terraform Variable Files

| File | Purpose |
|------|---------|
| `gcp/terraform.staging.tfvars` | Staging-specific values (small DB, single region, no HA Redis, scale-to-zero) |
| `gcp/terraform.prod.tfvars` | Production values (HA DB, multi-region Cloud Run, HA Redis, `deletion_protection = true`) |

Key differences between environments:

| Setting | Staging | Production |
|---------|---------|-----------|
| `environment` | `staging` | `prod` |
| `backend_regions` | `["us-central1"]` | `["us-central1", "us-east1"]` |
| `db_tier` | `db-g1-small` | `db-custom-2-7680` |
| `redis_tier` | `BASIC` | `STANDARD_HA` |
| `min_instances` | `0` (scale to zero) | `1` (always warm) |
| `ai_provider` | `mock` | `openai` |
| `deletion_protection` | `false` | `true` |
| `lb_domains` | `[]` (optional) | `["app.yourdomain.com"]` |

---

## First Deployment (Manual Bootstrap)

Run these commands **once** to bootstrap each environment. After this, CI/CD takes over.

```bash
# ── Step 1: Authenticate ──
gcloud auth application-default login

# ── Step 2: Bootstrap Staging ──
cd gcp

terraform init \
  -backend-config="bucket=taxrefund-staging-tfstate" \
  -backend-config="prefix=terraform/state"

terraform plan -var-file=terraform.staging.tfvars

# Review the plan carefully, then apply:
terraform apply -var-file=terraform.staging.tfvars

# Note the outputs:
terraform output
# frontend_bucket_name = "staging-taxrefund-backend-frontend-xxxx"
# load_balancer_ip     = "34.x.x.x"
# url_map_name         = "staging-taxrefund-backend-https"

# ── Step 3: Push the first Docker image manually ──
# (Only needed for first run; CI/CD handles subsequent builds)
gcloud auth configure-docker us-central1-docker.pkg.dev

IMAGE="us-central1-docker.pkg.dev/taxrefund-staging/taxrefund/taxrefund-backend:initial"
docker build -t "${IMAGE}" backend/
docker push "${IMAGE}"

# Re-apply to deploy this initial image:
terraform apply -var-file=terraform.staging.tfvars -var="artifact_image_tag=initial"

# ── Step 4: Bootstrap Production (same steps, different vars) ──
terraform init \
  -reconfigure \
  -backend-config="bucket=taxrefund-prod-tfstate" \
  -backend-config="prefix=terraform/state"

terraform plan -var-file=terraform.prod.tfvars
terraform apply -var-file=terraform.prod.tfvars
```

---

## Day-to-Day CI/CD Flows

### Backend changes (push to `main` or `develop`)

```
Push code
  │
  ▼
GitHub Actions: backend-cd.yml
  ├── mvn test                        (unit tests must pass)
  ├── mvn package
  ├── docker build + push             (tagged with git SHA → Artifact Registry)
  ├── terraform apply (staging)       (auto-approve, uses terraform.staging.tfvars)
  │
  └── [main only, requires approval]
      terraform apply (prod)          (uses terraform.prod.tfvars, same image SHA)
```

### Frontend changes (push to `main` or `develop`)

```
Push code
  │
  ▼
GitHub Actions: frontend-cd.yml
  ├── npm ci + npm run build
  ├── gcloud storage rsync → staging GCS bucket
  ├── Set index.html no-cache header
  ├── Invalidate CDN cache (staging)
  │
  └── [main only, requires approval]
      Deploy same build artifact → prod GCS bucket
      Invalidate CDN cache (prod)
```

### Infrastructure changes (push to `main` or `develop`, under `gcp/`)

```
Pull Request  →  terraform fmt + validate + plan (both environments, shown as PR comment)
Merge to main →  terraform apply staging → [approval] → terraform apply prod
```

---

## Terraform Commands Reference

All commands run from the `gcp/` directory.

```bash
cd gcp

# ── Init (required after first clone or backend change) ──
# Staging:
terraform init \
  -backend-config="bucket=taxrefund-staging-tfstate" \
  -backend-config="prefix=terraform/state"

# Production (use -reconfigure if you already inited for staging):
terraform init \
  -reconfigure \
  -backend-config="bucket=taxrefund-prod-tfstate" \
  -backend-config="prefix=terraform/state"

# ── Format check ──
terraform fmt -check -recursive

# ── Validate ──
terraform validate

# ── Plan ──
terraform plan -var-file=terraform.staging.tfvars
terraform plan -var-file=terraform.prod.tfvars

# ── Apply ──
terraform apply -var-file=terraform.staging.tfvars
terraform apply -var-file=terraform.prod.tfvars

# ── Deploy a specific image tag ──
terraform apply -var-file=terraform.staging.tfvars -var="artifact_image_tag=abc1234"

# ── View outputs ──
terraform output

# ── Destroy (staging only – deletion_protection=false required) ──
terraform destroy -var-file=terraform.staging.tfvars
```

---

## Backend `.tfvars` Variables Reference

Full list of all configurable variables in `variables.tf`:

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `project_id` | string | **required** | GCP project ID |
| `environment` | string | `prod` | Name prefix for all resources |
| `primary_region` | string | `us-central1` | Region for Cloud SQL primary + one Cloud Run |
| `backend_regions` | list(string) | `["us-central1","us-east1"]` | All regions to deploy Cloud Run |
| `db_read_replica_regions` | list(string) | `["us-east1"]` | Regions for read replicas |
| `service_name` | string | `taxrefund-backend` | Base name for resources |
| `artifact_repo` | string | `taxrefund` | Artifact Registry repo name |
| `artifact_image_tag` | string | `latest` | Docker image tag to deploy |
| `min_instances` | number | `1` | Cloud Run min instances |
| `max_instances` | number | `20` | Cloud Run max instances |
| `cpu` | string | `1` | Cloud Run CPU allocation |
| `memory` | string | `1Gi` | Cloud Run memory |
| `db_tier` | string | `db-custom-2-7680` | Cloud SQL machine type |
| `db_disk_size_gb` | number | `50` | Cloud SQL disk size |
| `redis_memory_gb` | number | `1` | Memorystore Redis size |
| `redis_tier` | string | `STANDARD_HA` | `BASIC` or `STANDARD_HA` |
| `ai_provider` | string | `mock` | `mock` or `openai` |
| `lb_domains` | list(string) | `[]` | Domains for managed SSL cert |
| `deletion_protection` | bool | `true` | Protect DB and Redis from destroy |

---

## Architectural Notes

### Why Cloud Run instead of GCE?
Cloud Run supports regional serverless deployment behind a **global external Application Load Balancer** using serverless NEGs. This gives global anycast ingress without managing VMs. Use GCE only if you need host-level OS control, custom networking, or long-running non-request-driven workloads.

### Read replicas are not automatic
The application must explicitly route read-only queries to replica endpoints. All writes go to the primary. The replica connection strings are available in Terraform outputs.

### CDN and cache invalidation
Static frontend assets are cached at Google's edge via Cloud CDN. `index.html` is served with `no-cache` headers so browsers always load the latest HTML entrypoint, which then loads hashed JS/CSS bundles that can be long-cached.

### HTTPS / managed certificates
Terraform provisions a Google-managed SSL certificate only when `lb_domains` is non-empty. Certificate issuance requires DNS to already resolve to the load balancer IP — provision the IP first, update DNS, then add the domain to `lb_domains`.

### Secret rotation
To rotate a secret (e.g. JWT key), add a new secret version in Secret Manager:
```bash
echo -n "new-value" | gcloud secrets versions add taxrefund-jwt-secret \
  --data-file=- --project=taxrefund-prod
```
Then redeploy Cloud Run (run the backend-cd workflow or trigger a manual `terraform apply`) to pick up the new version.

---

## Optional Future Improvements

- **Cloud Armor WAF**: Add rate limiting and OWASP rule sets to the backend service
- **Cross-project image promotion**: Copy images from the staging Artifact Registry to the prod registry for a cleaner separation of concerns
- **Cloud Run service-health failover**: Add health-check-based traffic splitting for stronger regional failover
- **Multi-region Redis**: Add a read replica or use Memorystore for Redis Cluster for lower read latency across regions  
- **Separate API and frontend hostnames**: Cleaner cache and CORS boundaries (e.g. `api.yourdomain.com` vs `app.yourdomain.com`)
- **Terraform workspaces**: Replace the separate `-backend-config` init pattern with named Terraform workspaces if you prefer a single state bucket
- **Slack/PagerDuty alerts**: Wire Cloud Monitoring alert policies to incident channels
