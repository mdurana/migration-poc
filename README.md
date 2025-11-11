# Migration POC (Java 25 / ShardingSphere 5.5.2)

This is a Proof of Concept (POC) for a database migration tool using ShardingSphere-Proxy and Liquibase. It is a multi-module Maven project.

This tool supports both **homogeneous** (MySQL $\rightarrow$ MySQL) and **heterogeneous** (MySQL $\rightarrow$ PostgreSQL) migrations, with an automated "best-effort" schema normalization step.

**This POC is NOT secure.** It uses plain-text passwords and disables SSL. It is for functional testing in an isolated sandbox ONLY.

## Components

* **`orchestrator`**: A Spring Boot app (Maven module) that runs the migration state machine.
* **`shardingsphere-proxy`**: The data migration engine (v5.5.2).
* **`zookeeper`**: Metadata store required by ShardingSphere.
* **`source-db`**: A MySQL 8 source database.
* **`target-db`**: An empty MySQL 8 target database.
* **`target-db-pg`**: An empty PostgreSQL 15 target database.
* **`scripts/populate-data.py` or `scripts/populate-data-pg.py`**: A helper script to load the source DB with test data.

## How to Run

### Prerequisites

* Docker & Docker Compose
* `curl` (for sending the API request)
* `python` and `pip` (Python 3.x recommended)
* **Python dependency**: `pip install mysql-connector-python`

### Step 1: Build & Start All Services

This command builds the Java application (using the multi-module `pom.xml`) and starts all 6 containers in detached mode. The `source-db` will be created with an **empty schema** (no data).

```bash
# For first time run
mvn wrapper:wrapper

# When there is a change
mvn clean package

rm generated-schema/*.xml

# For non-Apple Silicon Chip, SSP_CONFIG={mysql|pg}
SSP_CONFIG=mysql docker-compose up --build -d

# For Mac with Apple Silicon Chip
cd ssp-mac
SSP_CONFIG=mysql docker-compose up --build -d
````

Wait about 30-60 seconds for all services to initialize.

### Step 2: Populate the Source Database

Run the Python script from your host machine. This will connect to the `source-db` container (via its mapped port `3306`) and load it with 1,000 users and 5,000 orders.

```bash
# For first time run
pip install mysql-connector-python

python scripts/populate-data.py

# Or this
python3 scripts/populate-data.py
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

### Step 3: Trigger the Migration Job

Now that the source DB is full of data, trigger the migration. The `config/job-template.json` is pre-configured for a **heterogeneous** (MySQL $\rightarrow$ PostgreSQL) migration.

```bash
curl -X POST http://localhost:8080/job -H "Content-Type: application/json" -d @job-data/job-template.json
```

### Step 4: Monitor the Logs

This is the primary way to watch the migration. The job will run from start to finish without pausing.

```bash
# change to proper directory first, cd ssp-mac or cd ..
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

### Step 5: Test CDC (While DATA_RUNNING)

While the logs show the job is in the `DATA_RUNNING` state, open a **new terminal** and connect to the `mysql-source-db`:

```bash
docker-compose exec mysql-source-db mysql -u root -proot mysql_db
```

Once inside the MySQL prompt, insert a new row:

```sql
INSERT INTO users (name, email, `status`) VALUES ('cdc-test-user', 'cdc@example.com', 1);
COMMIT;
EXIT;
```

This change will be captured by ShardingSphere and replicated to the target.

### Step 6: Verify Results

Once the logs show the job is `DONE`:

#### A. Verify the Schema Artifacts

Check the `generated-schema/` directory on your host machine.

```bash
ls -l generated-schema/
```

For a heterogeneous job, you should see **two** files:

1.  `job-1-changelog.xml` (the raw, source-specific file)
2.  `job-1-changelog.normalized.xml` (the auto-translated file that was applied to the target)

#### B. Verify the Data

Connect to your **target database** to confirm all data was migrated.

```bash
# For a HETEROGENEOUS (PostgreSQL) target:
docker-compose exec pg-target-db psql -U postgres -d pg_db
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

### Step 7: Clean Up

This command stops and removes all containers, networks, and volumes (including all database data).

```bash
docker-compose down -v
```