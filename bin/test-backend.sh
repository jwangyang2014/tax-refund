#!/bin/bash

cd ../backend
set -a
source .env
set +a

./mvnw test