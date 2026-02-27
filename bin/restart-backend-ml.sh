#!/bin/bash

cd ../backend
set -a
source .env
set +a

cd ..
docker-compose down ml
docker-compose up -d ml
sleep 5000

curl -s -X POST http://localhost:8000/train
curl -s http://localhost:8000/model/info
