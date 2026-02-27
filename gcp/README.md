# Taxrefund GCP Terraform

This stack deploys a production-oriented Google Cloud setup for a Spring Boot backend and React frontend.

## Architecture

- **Backend**: Cloud Run deployed in multiple regions
- **Global ingress**: Global external Application Load Balancer
- **Backend routing**: Serverless NEGs attached to regional Cloud Run services
- **Frontend**: Cloud Storage backend bucket with **Cloud CDN**
- **Database**: Cloud SQL for PostgreSQL with **private IP**, **regional HA**, and optional **cross-region read replicas**
- **Cache**: Memorystore for Redis over private networking
- **Secrets**: Cloud Run reads runtime secrets from Secret Manager references

## Why Cloud Run instead of GCE?

Cloud Run supports regional serverless deployment and can sit behind a **global external Application Load Balancer** using **serverless NEGs**. That gives us global anycast ingress without managing VMs.

Use GCE only if you need:
- host-level OS control
- custom kernel/network tuning
- long-running processes that are a poor fit for request-driven scaling
- protocols/workloads that Cloud Run does not fit well

## Important app notes

1. **Cloud SQL read replicas do not help automatically.**  
   The application must explicitly send read-only traffic to replica endpoints. Writes still go to the primary.

2. **Cloud Run is deployed region-by-region in this stack** instead of using a single higher-level multi-region abstraction, because the load balancer + serverless NEG pattern maps cleanly to explicit regional backends.

3. **The backend is intended to be reached through the load balancer path.**  
   Cloud Run ingress is set so requests come through Google load balancing rather than exposing the service as a normal public internet endpoint path.

4. **The frontend bucket is public by design** so the load balancer and CDN can serve SPA assets. This is normal for public static frontend delivery.

5. **Multi-region does not automatically mean perfect app-level failover.**  
   Traffic enters Google at the nearest edge, but serverless backends do not automatically fail away from a region merely because the application there is returning errors. If you need stronger cross-region failover behavior, add the Cloud Run service-health pattern and test it explicitly.

6. **HTTPS requires domains in `lb_domains`.**  
   The Terraform creates a managed certificate only when `lb_domains` is non-empty.

## Folder layout

Terraform is under:

```bash
./gcp
```
# Deploy manually
```bash
cd gcp
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

# CI/CD flow
## Backend
- Build and test Spring Boot app
- Build Docker image
- Push immutable tag to Artifact Registry
- Run Terraform apply with artifact_image_tag=<git sha>

## Frontend
- Build React app
- Upload static files to the frontend GCS bucket
- Mark index.html as non-cacheable
- Invalidate CDN cache

## Terraform
- Validate on pull requests
- Apply on merge to main

# Required Terraform outputs

The workflows use:
- frontend_bucket_name
- url_map_name
- load_balancer_ip

These are defined in outputs.tf.

# DNS

After terraform apply, point your DNS records for the domains in lb_domains to:
- load_balancer_ip

# Optional next improvements
- Add Cloud Armor WAF / rate limiting to the backend service
- Split frontend and API onto separate hostnames if you want cleaner cache and CORS boundaries
- Add a more explicit multi-region Redis strategy if Redis becomes a latency bottleneck
- Add Cloud Run service-health based failover if you want stronger regional failover handling