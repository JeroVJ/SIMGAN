#!/bin/bash
set -a
source "$(dirname "$0")/.env"
set +a
exec mvn spring-boot:run -f "$(dirname "$0")/pom.xml"
