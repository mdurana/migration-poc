# POC Runbook & Operator's Guide

This runbook provides the step-by-step commands to execute and verify the migration POC.

## Prerequisites

### Required Software
* Docker & Docker Compose (v2.x or higher)
* `curl` (for sending API requests)
* `wget` or `curl` (for downloading dependencies)
* Python 3.x with `pip`
* MySQL client (for verification)
* PostgreSQL client (for verification)

### Python Dependencies
```bash
pip install mysql-connector-python
```

### ShardingSphere Proxy Dependencies
Before starting, download the required ZooKeeper client libraries:
```bash
# Create directory for external libraries
mkdir -p ext-lib

# Download ZooKeeper/Curator dependencies
cd ext-lib

wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar

cd ..
```

**Verify downloads:**
```bash
ls -lh ext-lib/
# Should show 5 JAR files totaling ~4-5 MB
```

## Step 1: Build & Start All Services

This command builds the Java application and starts all services (MySQL, PostgreSQL, ZooKeeper, ShardingSphere-Proxy, Orchestrator) in detached mode. The `source-db` will be initialized with an **empty schema**.
```bash
# Clean start (removes old data)
docker-compose down -v

# Build and start all services
docker-compose up --build -d
```

**Wait for services to initialize** (approximately 60-90 seconds).

### Verify Services are Running
```bash
# Check all containers are up
docker-compose ps

# Should show all services as "Up" or "healthy"
# NAME                    STATUS
# orchestrator            Up
# source-db               Up (healthy)
# target-db               Up (healthy)
# target-db-pg            Up (healthy)
# zookeeper               Up
# shardingsphere-proxy    Up
```

### Verify ShardingSphere Proxy Started Successfully

**Critical check** - Proxy must start successfully before proceeding:
```bash
docker logs shardingsphere-proxy 2>&1 | grep -i "start success"
```

**Expected output:**
```
[INFO] ShardingSphere-Proxy Cluster mode started successfully
```

**If you see errors**, check:
```bash
# Full logs
docker logs shardingsphere-proxy

# Common issues:
# 1. "No implementation class load" - Missing ZooKeeper JARs in ext-lib/
# 2. "Connection refused" to ZooKeeper - ZooKeeper not ready yet, wait 30s and restart proxy
# 3. YAML parsing errors - Check config/global.yaml syntax
```

### Test Proxy Connection
```bash
# From host machine (using mapped port 3309)
mysql -h localhost -P 3309 -u root -proot -e "SELECT 1"

# Should return:
# +---+
# | 1 |
# +---+
# | 1 |
# +---+
```

## Step 2: Populate the Source Database

Run the Python script from your host machine. This will connect to the `source-db` container (via its mapped port `3306`) and load it with 1,000 users and 5,000 orders.
```bash
python scripts/populate-data.py
```

**Expected Output:**
```
Connecting to source database...
Cleared existing data.
Generating 100000 users...
Users populated successfully.
Generating 500000 orders...
Orders populated successfully.

--- Verification ---
Total Users:  100000
Total Orders: 500000
--------------------
Database connection closed.
```

**Verify data in source:**
```bash
docker exec -it source-db mysql -u root -psource_password sourcedb -e "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM orders;"
```

## Step 3: Trigger the Migration Job

Now that the source database is full of data, trigger the migration job via the REST API.

### Option A: Heterogeneous Migration (MySQL → PostgreSQL)
```bash
curl -X POST http://localhost:8080/jobs \
     -H "Content-Type: application/json" \
     -d '{
       "jobName": "mysql-to-postgres-migration",
       "source": {
         "type": "mysql",
         "host": "source-db",
         "port": 3306,
         "database": "sourcedb",
         "user": "root",
         "password": "source_password"
       },
       "target": {
         "type": "postgresql",
         "host": "target-db-pg",
         "port": 5432,
         "database": "targetdb_pg",
         "user": "postgres",
         "password": "target_password_pg",
         "schema": "public"
       },
       "tablesToMigrate": ["users", "orders"]
     }'
```

### Option B: Homogeneous Migration (MySQL → MySQL)
```bash
curl -X POST http://localhost:8080/jobs \
     -H "Content-Type: application/json" \
     -d '{
       "jobName": "mysql-to-mysql-migration",
       "source": {
         "type": "mysql",
         "host": "source-db",
         "port": 3306,
         "database": "sourcedb",
         "user": "root",
         "password": "source_password"
       },
       "target": {
         "type": "mysql",
         "host": "target-db",
         "port": 3306,
         "database": "targetdb",
         "user": "root",
         "password": "target_password"
       },
       "tablesToMigrate": ["users", "orders"]
     }'
```

### Option C: Use Template File

If you have a `config/job-template.json` file:
```bash
curl -X POST http://localhost:8080/jobs \
     -H "Content-Type: application/json" \
     -d @config/job-template.json
```

**Expected response:**
```json
{
  "id": 1,
  "jobName": "mysql-to-postgres-migration",
  "status": "PENDING",
  "createdAt": "2025-10-21T14:00:00",
  "tablesTotal": 2,
  "tablesCompleted": 0,
  "progressPercentage": 0
}
```

**Save the job ID** (e.g., `1`) for monitoring.

## Step 4: Monitor the Migration

### A. Watch Orchestrator Logs (Primary Method)
```bash
docker logs -f orchestrator
```

**State machine transitions for a heterogeneous migration:**
```
INFO [Job-1] ========== MIGRATION LIFECYCLE STARTED ==========
INFO [Job-1] Status updated to: SCHEMA_GENERATING
INFO [Job-1] Generating schema from source...
INFO [Job-1] Snapshot created with 2 tables
INFO [Job-1] Changelog generated successfully at /app/generated-schema/job-1-changelog.xml

INFO [Job-1] Status updated to: SCHEMA_NORMALIZING
INFO [Job-1] Heterogeneous migration detected (mysql -> postgresql). Starting automated normalization.
INFO [Job-1] Normalizing type: mysql.TINYINT -> SMALLINT
INFO [Job-1] Normalized autoIncrement to SERIAL for column: id
INFO [Job-1] Schema normalization complete. Output: /app/generated-schema/job-1-changelog.normalized.xml

INFO [Job-1] Status updated to: SCHEMA_APPLYING
INFO [Job-1] Applying schema to target database...
INFO [Job-1] Starting Liquibase update on target...
INFO [Job-1] Schema applied successfully to target.

INFO [Job-1] Status updated to: DATA_CONFIGURING
INFO [Job-1] Registering storage units in ShardingSphere Proxy...
INFO [Job-1] ✓ Source storage unit registered
INFO [Job-1] ✓ Target storage unit registered
INFO [Job-1] Creating migration jobs for 2 tables...
INFO [Job-1] Created 2 migration jobs: [users, orders]

INFO [Job-1] Status updated to: DATA_RUNNING
INFO [Job-1] Starting data migration (inventory + CDC)...
INFO [Job-1] Monitoring migration progress...
INFO [Job-1] [Check #1] Job j01...: users - Status: EXECUTE_INVENTORY_TASK
INFO [Job-1] [Check #2] Job j01...: users - Status: EXECUTE_INVENTORY_TASK
INFO [Job-1] [Check #5] Job j01...: users - Status: EXECUTE_INCREMENTAL_TASK
INFO [Job-1] [Check #6] Job j02...: orders - Status: EXECUTE_INCREMENTAL_TASK
INFO [Job-1] ✓ All migration jobs are ready for cutover

INFO [Job-1] Status updated to: VALIDATING
INFO [Job-1] Running validation checks...
INFO [Job-1] ========== Starting Row Count Validation ==========
INFO [Job-1]   ✓ PASS - Table 'users': 1000 rows (source) == 1000 rows (target)
INFO [Job-1]   ✓ PASS - Table 'orders': 5000 rows (source) == 5000 rows (target)
INFO [Job-1] ========== Validation Summary ==========
INFO [Job-1] Total tables: 2
INFO [Job-1] Valid tables: 2
INFO [Job-1] Result: ✓ ALL VALIDATIONS PASSED

INFO [Job-1] Status updated to: COMMITTING
INFO [Job-1] Committing migration (cutover)...
INFO [Job-1] Committing migration job: users
INFO [Job-1] ✓ Consistency check passed
INFO [Job-1] ✓ Migration committed successfully
INFO [Job-1] Committing migration job: orders
INFO [Job-1] ✓ Migration committed successfully

INFO [Job-1] Status updated to: DONE
INFO [Job-1] ========== MIGRATION LIFECYCLE COMPLETE ==========
```

### B. Poll Job Status via API

In a separate terminal, monitor progress:
```bash
# Check every 5 seconds
watch -n 5 'curl -s http://localhost:8080/jobs/1 | python -m json.tool'
```

**Example output:**
```json
{
  "id": 1,
  "jobName": "mysql-to-postgres-migration",
  "status": "DATA_RUNNING",
  "createdAt": "2025-10-21T14:00:00",
  "updatedAt": "2025-10-21T14:05:30",
  "completedAt": null,
  "executionTimeMs": null,
  "tablesCompleted": 1,
  "tablesTotal": 2,
  "progressPercentage": 50,
  "lastError": null
}
```

### C. Monitor ShardingSphere Proxy Logs
```bash
docker logs -f shardingsphere-proxy
```

Look for:
- DistSQL execution logs
- Migration job status updates
- Any errors or warnings

## Step 5: Test CDC (Change Data Capture)

**While the job is in `DATA_RUNNING` state**, test that incremental changes are replicated.

### Insert New Data in Source

Open a **new terminal** and connect to the source database:
```bash
docker exec -it source-db mysql -u root -psource_password sourcedb
```

Inside the MySQL prompt:
```sql
-- Insert a test user
INSERT INTO users (name, email, status) 
VALUES ('cdc-test-user', 'cdc@example.com', 1);

-- Insert a test order
INSERT INTO orders (user_id, order_date, total_amount, status)
VALUES (1, NOW(), 99.99, 1);

-- Commit the changes
COMMIT;

-- Exit
EXIT;
```

### Wait for Replication

The changes should be replicated to the target within a few seconds. You'll see in the orchestrator logs:
```
INFO [Job-1] [Check #12] Job j01...: users - Status: EXECUTE_INCREMENTAL_TASK
```

This means CDC is actively replicating changes.

## Step 6: Verify Migration Results

Once the logs show the job status is `DONE`:

### A. Check Job Completion
```bash
curl -s http://localhost:8080/jobs/1 | grep -E '"status"|"completedAt"|"executionTimeMs"'
```

**Expected output:**
```json
  "status": "DONE",
  "completedAt": "2025-10-21T14:10:00",
  "executionTimeMs": 600000,
```

### B. Verify Generated Changelogs

Check the `generated-schema/` directory on your host machine:
```bash
ls -lh generated-schema/
```

**For a heterogeneous migration, you should see TWO files:**
1. `job-1-changelog.xml` - Raw changelog from source (MySQL-specific)
2. `job-1-changelog.normalized.xml` - Translated changelog (PostgreSQL-specific)

**Inspect the normalized changelog:**
```bash
grep -E "TINYINT|SERIAL|AUTO_INCREMENT" generated-schema/job-1-changelog.normalized.xml
```

You should see `SERIAL` instead of `AUTO_INCREMENT`, and `SMALLINT` instead of `TINYINT`.

### C. Verify Schema in Target Database

**For PostgreSQL target:**
```bash
docker exec -it target-db-pg psql -U postgres -d targetdb_pg
```

Inside psql:
```sql
-- List all tables
\dt

-- Describe users table structure
\d users

-- Should show:
--  id     | integer | not null default nextval('users_id_seq'::regclass)
--  name   | character varying(100)
--  email  | character varying(100)
--  status | smallint  -- Note: SMALLINT, not TINYINT

-- Check data types match PostgreSQL conventions
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;

-- Exit
\q
```

**For MySQL target:**
```bash
docker exec -it target-db mysql -u root -ptarget_password targetdb
```

Inside mysql:
```sql
-- Show table structure
DESCRIBE users;

-- Exit
EXIT;
```

### D. Verify Row Counts

**For PostgreSQL:**
```bash
docker exec -it target-db-pg psql -U postgres -d targetdb_pg -c "
  SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
  UNION ALL
  SELECT 'orders' AS table_name, COUNT(*) AS row_count FROM orders;
"
```

**For MySQL:**
```bash
docker exec -it target-db mysql -u root -ptarget_password targetdb -e "
  SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
  UNION ALL
  SELECT 'orders' AS table_name, COUNT(*) AS row_count FROM orders;
"
```

**Expected output:**
```
table_name | row_count
-----------+-----------
users      |      1001   (1000 initial + 1 CDC test)
orders     |      5001   (5000 initial + 1 CDC test)
```

### E. Verify CDC Test Data

**For PostgreSQL:**
```bash
docker exec -it target-db-pg psql -U postgres -d targetdb_pg -c "
  SELECT * FROM users WHERE name = 'cdc-test-user';
"
```

**For MySQL:**
```bash
docker exec -it target-db mysql -u root -ptarget_password targetdb -e "
  SELECT * FROM users WHERE name = 'cdc-test-user';
"
```

**Expected:** Should return the user you inserted during Step 5.

### F. Verify ShardingSphere Migration Jobs
```bash
# Connect to ShardingSphere Proxy
mysql -h localhost -P 3309 -u root -proot -e "SHOW MIGRATION LIST"
```

**Expected:** Should show your migration jobs with status `FINISHED`.

## Step 7: Clean Up

Stop and remove all containers, networks, and volumes (including all database data):
```bash
docker-compose down -v
```

**To preserve data for inspection:**
```bash
docker-compose down  # Keeps volumes
```

**To restart from scratch:**
```bash
docker-compose down -v
rm -rf generated-schema/*
docker-compose up --build -d
```

## Troubleshooting

### Issue: ShardingSphere Proxy Won't Start

**Symptoms:**
```
ERROR: No implementation class load from SPI 'org.apache.zookeeper.client.ZKClientConfig'
```

**Solution:**
Check that ZooKeeper JAR files are in `ext-lib/`:
```bash
ls -l ext-lib/
# Must have 5 JAR files
```

If missing, download them (see Prerequisites section).

**Restart proxy:**
```bash
docker-compose restart shardingsphere-proxy
docker logs -f shardingsphere-proxy
```

### Issue: Access Denied to Proxy

**Symptoms:**
```
ERROR 1045 (28000): Access denied for user 'root'@'172.18.0.X'
```

**Solution:**
Check `config/global.yaml` authority configuration:
```bash
docker exec shardingsphere-proxy cat /opt/shardingsphere-proxy/conf/global.yaml
```

Should have:
```yaml
authority:
  users:
    - user: root@%
      password: root
```

**Restart with corrected config:**
```bash
docker-compose restart shardingsphere-proxy
```

### Issue: Connection Refused to Proxy

**Symptoms:**
```
ERROR 2003 (HY000): Can't connect to MySQL server on 'shardingsphere-proxy:3307'
```

**Solution:**
1. Check if proxy container is running:
```bash
docker ps | grep shardingsphere-proxy
```

2. Check proxy logs for startup errors:
```bash
docker logs shardingsphere-proxy | tail -50
```

3. Check if port is listening:
```bash
docker exec shardingsphere-proxy netstat -tlnp | grep 3307
```

4. Verify ZooKeeper is running:
```bash
docker logs zookeeper | tail -20
```

### Issue: Migration Job Stuck in DATA_RUNNING

**Symptoms:**
Job stays in `DATA_RUNNING` for an unusually long time without progress.

**Solution:**

1. Check migration job status in proxy:
```bash
mysql -h localhost -P 3309 -u root -proot -e "SHOW MIGRATION LIST"
```

2. Get detailed status:
```bash
mysql -h localhost -P 3309 -u root -proot -e "SHOW MIGRATION STATUS 'job_id_here'"
```

3. Check for errors in proxy logs:
```bash
docker logs shardingsphere-proxy 2>&1 | grep -i error
```

4. Verify source database binlog is enabled:
```bash
docker exec source-db mysql -u root -psource_password -e "SHOW VARIABLES LIKE 'log_bin'"
# Should show: log_bin | ON
```

### Issue: Validation Fails (Row Count Mismatch)

**Symptoms:**
```
ERROR [Job-1]   ✗ FAIL - Table 'users': 1000 rows (source) != 999 rows (target)
```

**Solution:**

1. Check if migration completed:
```bash
mysql -h localhost -P 3309 -u root -proot -e "SHOW MIGRATION LIST"
```

2. Manually verify counts:
```bash
# Source
docker exec source-db mysql -u root -psource_password sourcedb -e "SELECT COUNT(*) FROM users"

# Target
docker exec target-db-pg psql -U postgres -d targetdb_pg -c "SELECT COUNT(*) FROM users"
```

3. Check for migration errors:
```bash
docker logs shardingsphere-proxy 2>&1 | grep -i error
```

### Issue: Schema Application Fails

**Symptoms:**
```
ERROR [Job-1] Failed to apply changelog to target
```

**Solution:**

1. Check Liquibase logs in orchestrator:
```bash
docker logs orchestrator 2>&1 | grep -i liquibase
```

2. Inspect the generated changelog:
```bash
cat generated-schema/job-1-changelog.normalized.xml
```

3. Verify target database is empty:
```bash
# For PostgreSQL
docker exec target-db-pg psql -U postgres -d targetdb_pg -c "\dt"
```

4. If target has conflicting schema, drop and recreate:
```bash
docker-compose down -v
docker-compose up -d
```

### Issue: Out of Memory

**Symptoms:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution:**
Increase Java heap size in `docker-compose.yml`:
```yaml
orchestrator:
  environment:
    - JAVA_OPTS=-Xmx2g -Xms512m
```

## Performance Notes

### Expected Timing (6000 rows)

| Phase | Duration |
|-------|----------|
| Schema Generation | 5-10 seconds |
| Schema Normalization | 1-2 seconds |
| Schema Application | 5-10 seconds |
| Data Configuration | 5-10 seconds |
| Inventory Migration | 10-30 seconds |
| Incremental Sync | 5-10 seconds |
| Validation | 5-10 seconds |
| Commit | 5-10 seconds |
| **Total** | **1-2 minutes** |

### Larger Datasets

For datasets > 100K rows:
- Inventory phase will be longer (proportional to data size)
- Incremental sync lag may vary based on write load
- Consider adjusting batch sizes in `global.yaml`:
```yaml
  migration:
    props:
      batch-size: 2000  # Increase for better throughput
      worker-thread: 40   # More parallel workers
```

## Quick Reference

### Important Ports

| Service | Container Port | Host Port | Protocol |
|---------|---------------|-----------|----------|
| source-db | 3306 | 3306 | MySQL |
| target-db | 3306 | 3307 | MySQL |
| target-db-pg | 5432 | 5432 | PostgreSQL |
| zookeeper | 2181 | 2181 | ZooKeeper |
| shardingsphere-proxy | 3307 | 3309 | MySQL Protocol |
| orchestrator | 8080 | 8080 | HTTP |

### Container Connection Patterns

**From Host:**
- Proxy: `mysql -h localhost -P 3309 -u root -proot`
- Source: `mysql -h localhost -P 3306 -u root -psource_password sourcedb`
- Target (MySQL): `mysql -h localhost -P 3307 -u root -ptarget_password targetdb`
- Target (PG): `psql -h localhost -p 5432 -U postgres -d targetdb_pg`

**From Orchestrator Container:**
- Proxy: `jdbc:mysql://shardingsphere-proxy:3307/`
- Source: `jdbc:mysql://source-db:3306/sourcedb`
- Target (MySQL): `jdbc:mysql://target-db:3306/targetdb`
- Target (PG): `jdbc:postgresql://target-db-pg:5432/targetdb_pg`

### Useful Commands
```bash
# View all logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f orchestrator
docker-compose logs -f shardingsphere-proxy

# Check service health
docker-compose ps

# Restart a service
docker-compose restart orchestrator

# Rebuild and restart
docker-compose up --build -d orchestrator

# Execute SQL in databases
docker exec -it source-db mysql -u root -psource_password sourcedb
docker exec -it target-db-pg psql -U postgres -d targetdb_pg

# Check migration status
mysql -h localhost -P 3309 -u root -proot -e "SHOW MIGRATION LIST"
```

## Success Checklist

After completing the runbook, verify:

- [ ] All 6 services started successfully
- [ ] ShardingSphere Proxy shows "start success" in logs
- [ ] 6000 rows populated in source database (1000 users + 5000 orders)
- [ ] Migration job created with status `PENDING`
- [ ] Job progressed through all states without errors
- [ ] CDC test data replicated to target
- [ ] Final status shows `DONE`
- [ ] Row counts match between source and target (including CDC test)
- [ ] Generated changelogs exist in `generated-schema/`
- [ ] For heterogeneous migration: normalized changelog shows correct type translations
- [ ] Schema in target follows target database conventions

**If all items are checked, the POC is successful! 🎉**