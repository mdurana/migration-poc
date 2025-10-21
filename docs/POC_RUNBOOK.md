# POC Runbook & Operator's Guide

This runbook provides the step-by-step commands to execute and verify the migration POC.

## Prerequisites
* Docker & Docker Compose
* `curl` (for sending the API request)
* `python` and `pip` (Python 3.x recommended)
* **Python dependency**: `pip install mysql-connector-python`

## Step 1: Build & Start All Services
This command builds the Java application and starts all services (MySQL, PostgreSQL, ZK, SS-Proxy, Orchestrator) in detached mode. The `source-db` will be created with an **empty schema** (no data).

```bash
docker-compose up --build -d
````

Wait about 30-60 seconds for all services to initialize.

## Step 2: Populate the Source Database

Run the new Python script from your host machine. This will connect to the `source-db` container (via its mapped port `3306`) and load it with 1,000 users and 5,000 orders.

```bash
# pip install mysql-connector-python
python scripts/populate-data.py
```

**Expected Output:**

```
Connecting to source database...
Cleared existing data.
Generating 1000 users...
Users populated successfully.
Generating 5000 orders...
Orders populated successfully.

--- Verification ---
Total Users:  1000
Total Orders: 5000
--------------------
Database connection closed.
```

## Step 3: Trigger the Migration Job

Now that the source DB is full of data, trigger the migration. You can use `config/job-template.json` for a heterogeneous (MySQL $\rightarrow$ PostgreSQL) test.

```bash
curl -X POST http://localhost:8080/job \
     -H "Content-Type: application/json" \
     -d @config/job-template.json
```

## Step 4: Monitor the Logs

This is the primary way to watch the migration. The job will now run from start to finish without pausing.

```bash
docker-compose logs -f orchestrator
```

You are looking for the state machine transitions for a heterogeneous run:

```
INFO [Job-1] [STATE] PENDING -> SCHEMA_GENERATING
INFO ... Changelog generated successfully at /app/generated-schema/job-1-changelog.xml
INFO [Job-1] Heterogeneous migration detected. Starting automated normalization.
INFO [Job-1] [STATE] SCHEMA_GENERATING -> SCHEMA_NORMALIZING
INFO ... Normalizing type: mysql.TINYINT -> SMALLINT
INFO ... Normalized autoIncrement to SERIAL for node: id
INFO ... Normalization complete. Normalized changelog saved to /app/generated-schema/job-1-changelog.normalized.xml
INFO [Job-1] [STATE] SCHEMA_NORMALIZING -> SCHEMA_APPLYING
INFO ... Starting Liquibase update on target...
INFO [Job-1] [STATE] SCHEMA_APPLYING -> DATA_CONFIGURING
...
INFO [Job-1] [POLL] Status: INVENTORY, Progress: 95%
...
INFO [Job-1] [POLL] Status: ALMOST_FINISHED, Lag (ms): ...
INFO [Job-1] CDC is in sync. Proceeding.
INFO [Job-1] [STATE] DATA_RUNNING -> VALIDATING
INFO [Job-1] [VALIDATE] Table 'users': Source(1000) == Target(1000) -> PASS
INFO [Job-1] [VALIDATE] Table 'orders': Source(5000) == Target(5000) -> PASS
INFO [Job-1] [STATE] VALIDATING -> DONE
INFO [Job-1] Lifecycle COMPLETE.
```

## Step 5: Test CDC (While DATA\_RUNNING)

While the logs show the job is in the `DATA_RUNNING` state, open a **new terminal** and connect to the `source-db`:

```bash
docker-compose exec source-db mysql -u root -psource_password sourcedb
```

Once inside the MySQL prompt, insert a new row:

```sql
INSERT INTO users (name, email, `status`) VALUES ('cdc-test-user', 'cdc@example.com', 1);
COMMIT;
EXIT;
```

This change will be captured by ShardingSphere and replicated to the target.

## Step 6: Verify Results

Once the logs show the job is `DONE`:

**A. Verify the Schema Artifacts:**
Check the `generated-schema/` directory on your host machine.

```bash
ls -l generated-schema/
```

For a heterogeneous job, you should see **two** files:

1.  `job-1-changelog.xml` (the raw, source-specific file)
2.  `job-1-changelog.normalized.xml` (the auto-translated file that was applied to the target)

**B. Verify the Data:**
Connect to your **target database** to confirm all data was migrated.

```bash
# For a HETEROGENEOUS (PostgreSQL) target:
docker-compose exec target-db-pg psql -U postgres -d targetdb_pg

# For a HOMOGENEOUS (MySQL) target:
# docker-compose exec target-db mysql -u root -ptarget_password targetdb
```

Run queries to check the data (using PostgreSQL syntax if applicable):

```sql
-- Check that the CDC test user exists (total users should be 1001)
SELECT * FROM users WHERE name = 'cdc-test-user';

-- Check the row counts
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM orders;
```

You should see `1001` users (1000 from the script + 1 from your CDC test) and `5000` orders.

## Step 7: Clean Up

This command stops and removes all containers, networks, and volumes (including all database data).

```bash
docker-compose down -v
```