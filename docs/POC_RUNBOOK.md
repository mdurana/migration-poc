# POC Runbook & Operator's Guide

This runbook provides the step-by-step commands to execute and verify the migration POC.

## Prerequisites
* Docker & Docker Compose
* A command-line shell (bash, zsh, etc.)
* `curl` (for sending the API request)
* `mvnw` (Maven wrapper, included) for building

## Step 1: Configure Source (Optional)
The `source-db` is populated by the file `scripts/init-source.sql`. If you want to test with different tables or data, modify this file *before* starting the services.

## Step 2: Build & Start All Services
This command builds the Java application and starts all 5 containers in detached mode.

```bash
docker-compose up --build -d
````

Wait about 30-60 seconds for all services (especially Zookeeper and the databases) to initialize.

## Step 3: Trigger the Migration Job

From your host machine, `POST` the job template to the orchestrator's API.

```bash
curl -X POST http://localhost:8080/job \
     -H "Content-Type: application/json" \
     -d @config/job-template.json
```

This will return a JSON object representing the newly created job in its `PENDING` state.

## Step 4: Monitor the Logs

This is the primary way to watch the migration. Tail the logs of the `orchestrator` container.

```bash
docker-compose logs -f orchestrator
```

You are looking for the state machine transitions in the log output:

```
INFO [Job-1] [STATE] PENDING -> SCHEMA_GENERATING
INFO [Job-1] Status updated to: SCHEMA_GENERATING
INFO ... Generating Liquibase changelog from source...
INFO ... Changelog generated successfully at /app/generated-schema/job-1-changelog.xml
INFO [Job-1] [STATE] SCHEMA_GENERATING -> SCHEMA_APPLYING
INFO [Job-1] Status updated to: SCHEMA_APPLYING
INFO ... Starting Liquibase update on target...
INFO [Job-1] [STATE] SCHEMA_APPLYING -> DATA_CONFIGURING
...
INFO [Job-1] [POLL] Status: INVENTORY, Progress: 75%
...
INFO [Job-1] [POLL] Status: ALMOST_FINISHED, Lag (ms): 123
INFO [Job-1] CDC is in sync. Proceeding.
INFO [Job-1] [STATE] DATA_RUNNING -> VALIDATING
...
INFO [Job-1] [VALIDATE] Table 'users': Source(100) == Target(100) -> PASS
INFO [Job-1] [STATE] VALIDATING -> DONE
INFO [Job-1] Lifecycle COMPLETE.
```

## Step 5: Test CDC (While DATA\_RUNNING)

While the logs show the job is in the `DATA_RUNNING` state (specifically after the `INVENTORY` phase is done), open a **new terminal** and connect to the `source-db`:

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

**A. Verify the Schema Artifact:**
Check the `generated-schema/` directory on your host machine.

```bash
ls -l generated-schema/
```

You should see the `job-1-changelog.xml` (or similar) file.

**B. Verify the Data:**
Connect to the `target-db` to confirm all data was migrated.

```bash
docker-compose exec target-db mysql -u root -ptarget_password targetdb
```

Run queries to check the data:

```sql
-- Check that the CDC test user exists
SELECT * FROM users WHERE name = 'cdc-test-user';

-- Check the row counts (should match the validation log)
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM orders;
```

## Step 7: Clean Up

This command stops and removes all containers, networks, and volumes (including the database data).

```bash
docker-compose down -v
```