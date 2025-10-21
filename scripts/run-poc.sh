#!/bin/bash
#
# This script builds the entire project, starts all Docker services,
# waits for them to initialize, and then populates the source database
# with test data.
#

# Exit immediately if any command fails
set -e

echo "Building Maven project (this may take a moment)..."
./mvnw clean package -DskipTests

echo "Building and starting Docker Compose services in detached mode..."
docker-compose up --build -d

echo "Waiting 20 seconds for services (especially databases) to initialize..."
sleep 20

echo "Populating source database with test data..."
python scripts/populate-data.py

echo ""
echo "✅ Setup complete. All services are running and data is loaded."
echo ""
echo "You can now trigger the migration. In a new terminal, run:"
echo "curl -X POST http://localhost:8080/job -H \"Content-Type: application/json\" -d @config/job-template.json"
echo ""
echo "Then, monitor the logs with:"
echo "docker-compose logs -f orchestrator"