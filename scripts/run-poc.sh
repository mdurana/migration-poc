#!/bin/bash
set -e

echo "Building Maven project..."
./mvnw clean package -DskipTests

echo "Starting Docker Compose services..."
docker-compose up --build -d

echo ""
echo "Services are starting. Tailing orchestrator logs:"
echo "Press Ctrl+C to stop logging."
echo ""

docker-compose logs -f orchestrator