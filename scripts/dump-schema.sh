#!/bin/bash
# Helper to dump the schema from the running source-db container

echo "Dumping schema from source-db..."
docker-compose exec -T source-db mysqldump -u root -psource_password --no-data --routines --triggers sourcedb > ./schema/mysql-schema-dump.sql
echo "Schema saved to ./schema/mysql-schema-dump.sql"