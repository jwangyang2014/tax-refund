#!/bin/bash

cd ../backend
set -a
source .env
set +a

./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005'
