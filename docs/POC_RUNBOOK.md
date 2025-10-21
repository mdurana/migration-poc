# POC Runbook & Operator's Guide

This runbook provides the step-by-step commands to execute and verify the migration POC, including the automated heterogeneous workflow.

## Prerequisites
* Docker & Docker Compose
* A command-line shell (bash, zsh, etc.)
* `curl` (for sending the API request)
* `mvnw` (Maven wrapper, included) for building

## Step 1: Configure Source (Optional)
The `source-db` is populated by the file `scripts/init-source.sql`. This script creates the sample **schema and data** in the source database when it first starts.

## Step 2: Build & Start All Services
This command builds the Java application and starts all services (MySQL, PostgreSQL, ZK, SS-Proxy, Orchestrator) in detached mode.

```bash
docker-compose up --build -d
````

Wait about 30-60 seconds for all services to initialize.

## Step 3: Trigger the Migration Job

From your host machine, `POST` the job template to the orchestrator's API. You can use the standard `job-template.json` for a homogeneous (MySQL $\rightarrow$ MySQL) test or a new template for a heterogeneous (MySQL $\rightarrow$ PostgreSQL) test.

```bash
# Example: Triggering a heterogeneous migration
# Make sure you have a 'job-template-mysql-to-pg.json' or similar
curl -X POST http://localhost:8080/job \
     -H "Content-Type: application/json" \
     -d @config/job-template.json 
```

## Step 4: Monitor the Logs

This is the primary way to watch the migration. The job will now run from start to finish without pausing, whether it's homogeneous or heterogeneous.

```bash
docker-compose logs -f orchestrator
```

You are looking for the **new** state machine transitions for a heterogeneous run:

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
INFO [Job-1] [POLL] Status: ALMOST_FINISHED, Lag (ms): 123
INFO [Job-1] CDC is in sync. Proceeding.
INFO [Job-1] [STATE] DATA_RUNNING -> VALIDATING
...
INFO [Job-1] [VALIDATE] Table 'users': Source(3) == Target(3) -> PASS
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
INSERT INTO users (name) VALUES ('cdc-test-user');
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

For a heterogeneous job, you should now see **two** files:

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
-- Check that the CDC test user exists
SELECT * FROM users WHERE name = 'cdc-test-user';

-- Check the row counts (should match the validation log)
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM orders;
```

## Step 7: Clean Up

This command stops and removes all containers, networks, and volumes (including all database data).

```bash
docker-compose down -v
```