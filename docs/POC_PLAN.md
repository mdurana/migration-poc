# POC Development Plan: ShardingSphere Migration

## 1. Objective
This document outlines the plan for a Proof of Concept (POC) to validate the core workflow of a database migration tool.

The primary goal is to prove the functional correctness of using **Liquibase** for automated schema generation and **ShardingSphere-Proxy (DistSQL)** for data migration in both **homogeneous (MySQL → MySQL)** and **heterogeneous (MySQL → PostgreSQL)** environments.

It also validates an *automated* schema normalization step for basic heterogeneous migrations, removing the need for manual intervention.

## 2. Tech Stack
* **Runtime:** Java 25 (with `--enable-preview` features enabled), Spring Boot 3.5.6
* **Schema Migration:** Liquibase 4.31.1 (using modern snapshot-based changelog generation)
* **Data Migration:** Apache ShardingSphere-Proxy 5.5.2 (with DistSQL for migration orchestration)
* **Build:** Apache Maven (multi-module) with `maven-compiler-plugin:3.14.0`
* **Containerization:** Docker Compose
* **Data Generation:** Python (`mysql-connector-python`)
* **Metadata (Orchestrator):** SQLite 3.49.1.0 (with Hibernate 6.6.29.Final)
* **Metadata (ShardingSphere):** ZooKeeper 3.9 (stores migration job state and progress)
* **Lombok:** 1.18.36 (annotation processing for Java 25)

## 3. Architecture
The POC consists of 6 containerized services managed by `docker-compose.yml`, plus one local script:

1.  **`orchestrator`**: A Spring Boot application (Maven module) containing the state machine and API. It acts as the "brain" and runs all tasks. Connects to ShardingSphere-Proxy via MySQL protocol on port **3307** (container internal port).
2.  **`mysql-source-db`**: A MySQL 8 database, initialized with schema and data. **Container port:** 3306 | **Host port:** 3306
3.  **`mysql-target-db`**: An empty MySQL 8 database (for homogeneous tests). **Container port:** 3306 | **Host port:** 3307
4.  **`pg-source-db`**: An empty PostgreSQL 11 database (for heterogeneous tests). **Container port:** 5432 | **Host port:** 5432
4.  **`pg-target-db`**: An empty PostgreSQL 16 database (for heterogeneous tests). **Container port:** 5432 | **Host port:** 5432
5.  **`shardingsphere-proxy`**: The data migration engine. **Container port:** 3307 (MySQL/PostgreSQL protocol) | **Host port:** 3309. Requires ZooKeeper client libraries in `ext-lib/` directory.
6.  **`zookeeper`**: The metadata store required by ShardingSphere for migration job state in Cluster mode. **Port:** 2181
7.  **`scripts/populate-data.py`**: A local Python script used to generate and load a large (6,000+ row) test dataset into the `source-db` *after* it starts.

### Container Network Communication
- **Orchestrator → Proxy**: Uses container name `shardingsphere-proxy` and container port `3307`
- **Proxy → Databases**: Uses container names (`mysql-source-db`, `mysql-target-db`, `pg-source-db`, `pg-target-db`) and container port `3306` (MySQL) or `5432` (PostgreSQL)
- **Proxy → ZooKeeper**: Uses container name `zookeeper` and port `2181`
- **Host → Proxy**: Uses `localhost` and mapped host port `3309`

### ShardingSphere Proxy Configuration
The proxy is configured via `global.yaml` (not `server.yaml` in 5.5.2) with:
- **Mode:** Cluster (using ZooKeeper for distributed state)
- **Authority:** MySQL-compatible authentication with user `root@%`
- **Frontend Protocol:** MySQL (port 3307)
- **Backend Driver:** JDBC
- **Required Dependencies:** ZooKeeper client JARs must be present in `ext-lib/`:
  - `mysql-connector-j-8.4.0.jar`

## 4. Core Workflow (State Machine)
The entire migration is triggered by a single `POST /job` API call. The `JobService` then executes the following states asynchronously:

1.  **`PENDING`**: The job is created in the SQLite DB.

2.  **`SCHEMA_GENERATING`**: The `SchemaExecutor` connects to the `source-db` and generates a Liquibase changelog by:
    - Creating a database snapshot of the source schema
    - Comparing against an empty reference database
    - Generating XML changelog with full DDL (CREATE TABLE, indexes, constraints, etc.)

3.  **`SCHEMA_GENERATE_FAILED`**: (Error state)

4.  **`SCHEMA_NORMALIZING`**: **(Conditional)** If a heterogeneous migration is detected (source type ≠ target type), this automated step:
    - Parses the generated XML changelog
    - Translates source-specific data types using the `dataTypeMappings` configuration
    - Handles special cases like MySQL `AUTO_INCREMENT` → PostgreSQL `SERIAL`
    - Preserves type parameters (e.g., `VARCHAR(100)`)
    - Outputs a normalized changelog file

5.  **`SCHEMA_NORMALIZE_FAILED`**: (Error state)

6.  **`SCHEMA_APPLYING`**: The `SchemaExecutor` connects to the `*-target-db` and applies the schema using:
    - The raw changelog for homogeneous migrations
    - The normalized changelog for heterogeneous migrations
    - Liquibase's `update` command to create all tables, indexes, and constraints

7.  **`SCHEMA_FAILED`**: (Error state)

8.  **`DATA_CONFIGURING`**: The `DataExecutor` configures ShardingSphere-Proxy by:
    - Registering source database as a storage unit using `REGISTER STORAGE UNIT source_ds (...)`
    - Registering target database as a storage unit using `REGISTER STORAGE UNIT target_ds (...)`
    - Creating migration jobs for each table using `MIGRATE TABLE source_ds.table INTO table`
    - Verifying registration with `SHOW STORAGE UNITS`

9.  **`DATA_CONFIG_FAILED`**: (Error state)

10. **`DATA_RUNNING`**: The `DataExecutor` monitors migration progress by:
    - Polling `SHOW MIGRATION LIST` every 5 seconds
    - Tracking inventory phase (bulk data copy)
    - Tracking incremental phase (CDC/binlog replication)
    - Waiting until all jobs reach `EXECUTE_INCREMENTAL_TASK` status (ready for cutover)
    - This state covers both **inventory (bulk load)** and **incremental (CDC)** phases

11. **`DATA_FAILED`**: (Error state)

12. **`VALIDATING`**: The `ValidationExecutor` performs data quality checks:
    - Executes `SELECT COUNT(*)` on all tables in both source and target
    - Uses proper schema qualification (e.g., `public.table` for PostgreSQL, `database.table` for MySQL)
    - Validates table names to prevent SQL injection
    - Compares row counts and logs detailed results
    - Queries migration job status using `SHOW MIGRATION STATUS 'job_id'`

13. **`VALIDATION_FAILED`**: (Error state)

14. **`COMMITTING`**: The `DataExecutor` finalizes the migration:
    - Runs consistency check: `CHECK MIGRATION 'job_id'`
    - Commits each migration job: `COMMIT MIGRATION 'job_id'`
    - This performs the cutover, making the target the active database

15. **`COMMIT_FAILED`**: (Error state)

16. **`DONE`**: The job is complete and successful. The target database now contains the migrated schema and data.

### Error Handling and Rollback
- If any step fails, the job transitions to the corresponding error state
- For failures during or after `DATA_CONFIGURING`, the system attempts to rollback migration jobs using `ROLLBACK MIGRATION 'job_id'`
- Error messages are captured in the `lastError` field of the Job entity
- Failed jobs can be inspected via the REST API at `GET /jobs/{id}`

## 5. Key Artifacts

### Generated Files
* **Changelogs**: All generated changelogs are saved to the host machine in the `./generated-schema` directory for audit and debugging:
  - `job-{id}-changelog.xml`: Raw Liquibase changelog from source database
  - `job-{id}-changelog.normalized.xml`: Translated changelog for heterogeneous migrations
  
### Logs
* **Orchestrator Logs**: All state transitions, SQL execution, and progress tracked via SLF4J
  - View with: `docker logs -f orchestrator`
* **Proxy Logs**: ShardingSphere-Proxy startup, DistSQL execution, migration progress
  - View with: `docker logs -f shardingsphere-proxy`
* **ZooKeeper Logs**: Cluster coordination and metadata persistence
  - View with: `docker logs zookeeper`

### Job Metadata
* **Job Entity**: Persisted in SQLite with tracking for:
  - Execution time (calculated automatically on completion)
  - Progress tracking (`tablesCompleted` / `tablesTotal`)
  - Progress percentage (calculated property)
  - Completion timestamp
  - Full request JSON for audit trail

### Migration Jobs
* **Per-Table Jobs**: ShardingSphere 5.5.2 creates one migration job per table
  - Job IDs tracked in memory during execution
  - Status queryable via `SHOW MIGRATION LIST` and `SHOW MIGRATION STATUS 'job_id'`
  - Metadata stored in ZooKeeper under `/migration_poc` namespace

## 6. REST API

### Create and Start Migration Job
```http
POST /jobs
Content-Type: application/json

{
  "jobName": "postgresql-to-postgresql-migration",
  "source": {
    "type": "postgresql",
    "host": "pg-source-db",
    "port": 5432,
    "database": "pg_db",
    "user": "postgres",
    "password": "postgres"
  },
  "target": {
    "type": "postgresql",
    "host": "pg-target-db",
    "port": 5432,
    "database": "pg_db",
    "user": "postgres",
    "password": "postgres"
  },
  "tablesToMigrate": ["users", "orders"]
}
```

### Get Job Status
```http
GET /jobs/{id}

Response:
{
  "id": 1,
  "jobName": "postgresql-to-postgresql-migration",
  "status": "DATA_RUNNING",
  "createdAt": "2025-10-21T14:00:00",
  "updatedAt": "2025-10-21T14:05:30",
  "completedAt": null,
  "executionTimeMs": null,
  "tablesCompleted": 2,
  "tablesTotal": 3,
  "progressPercentage": 66,
  "lastError": null
}
```

## 7. Data Type Mappings (Heterogeneous Migrations)

### MySQL → PostgreSQL Common Mappings
```json
{
  "mysql.TINYINT": "SMALLINT",
  "mysql.MEDIUMINT": "INTEGER",
  "mysql.BIGINT": "BIGINT",
  "mysql.FLOAT": "REAL",
  "mysql.DOUBLE": "DOUBLE PRECISION",
  "mysql.DATETIME": "TIMESTAMP",
  "mysql.TEXT": "TEXT",
  "mysql.MEDIUMTEXT": "TEXT",
  "mysql.LONGTEXT": "TEXT",
  "mysql.BLOB": "BYTEA",
  "mysql.MEDIUMBLOB": "BYTEA",
  "mysql.LONGBLOB": "BYTEA"
}
```

### Special Cases
- **AUTO_INCREMENT**: Automatically converted to `SERIAL` for PostgreSQL
- **VARCHAR(n)**: Parameter `(n)` is preserved during type translation
- **ENUM**: Requires manual handling (out of scope for POC)

## 8. Testing Strategy

### Homogeneous Migration (MySQL → MySQL)
1. Start all services: `docker-compose up -d`
2. Wait for proxy to be healthy: `docker logs -f shardingsphere-proxy`
3. Populate source data: `python scripts/populate-data.py`
4. Trigger migration: `curl -X POST http://localhost:8080/jobs -H "Content-Type: application/json" -d @config/job-template-mysql.json`
5. Monitor progress: `curl http://localhost:8080/jobs/1`
6. Verify completion: Check `status: "DONE"` and validate row counts in target

### Heterogeneous Migration (MySQL → PostgreSQL)
1. Start all services: `docker-compose up -d`
2. Wait for proxy to be healthy
3. Populate source data: `python scripts/populate-data.py`
4. Trigger migration: `curl -X POST http://localhost:8080/jobs -H "Content-Type: application/json" -d @config/job-template-postgres.json`
5. Monitor progress: `curl http://localhost:8080/jobs/1`
6. Inspect normalized changelog: `cat generated-schema/job-1-changelog.normalized.xml`
7. Verify completion and validate data types match PostgreSQL conventions

### Validation Checks
- Row counts match between source and target
- Schema structure correctly applied (check with `\d table` in psql or `DESCRIBE table` in mysql)
- Data types properly translated for heterogeneous migrations
- Indexes and constraints created correctly
- No data loss during migration

## 9. Out of Scope (Explicitly)

### Security
* All connections use plain-text passwords and disable SSL
* No encryption for data in transit
* No RBAC or user management beyond basic authentication
* Suitable for POC/development only - **NOT production-ready**

### Advanced Schema Features
* The automated normalization step **only** handles:
  - Basic data type translations
  - AUTO_INCREMENT → SERIAL conversion
  - Parameter preservation for sized types (VARCHAR, DECIMAL, etc.)
* **Not supported:**
  - Stored procedures and functions
  - Triggers
  - Views (included in changelog but not translated)
  - Custom data types (ENUM, SET)
  - Partitioning strategies
  - Complex CHECK constraints with database-specific syntax

### Scale and Performance
* **Not a VLDB solution**: This POC does not test true Very Large Databases (multi-terabyte scale)
* **Test dataset**: Validates workflow with 6,000+ rows (non-trivial but not production-scale)
* **No optimization**: Default settings used for Liquibase and ShardingSphere
* **Single-instance**: No distributed execution or parallel table migration

### Operational Concerns
* **High Availability**: If the `orchestrator` container crashes, the job stops
* **No resume capability**: Failed jobs cannot be resumed; must restart from beginning
* **Manual recovery**: Use `docker-compose down -v` and restart to clear state
* **No monitoring**: Basic logging only, no metrics/alerting
* **No backup/restore**: No automated backup of source before migration

### Data Quality
* **Row count validation only**: Does not verify:
  - Data content correctness
  - Character encoding issues
  - Precision loss in numeric conversions
  - Date/time timezone handling
* **No checksums**: No MD5/SHA verification of data integrity

### Network and Infrastructure
* **Local Docker only**: Not tested with remote databases or cloud providers
* **No network resilience**: Does not handle network partitions or timeouts gracefully
* **Fixed ports**: Port conflicts require manual docker-compose.yml edits

## 10. Known Limitations and Workarounds

### ShardingSphere Proxy Startup
**Issue**: Proxy requires ZooKeeper client libraries not included in the base image.
**Workaround**: Download and mount 5 JAR files to `ext-lib/` directory (see Architecture section).

### Connection String Confusion
**Issue**: Container vs. host port mapping (3307 vs 3309).
**Workaround**: Orchestrator uses container port 3307, host testing uses port 3309.

### Job Naming
**Issue**: Multiple migrations with the same name are allowed (no unique constraint).
**Rationale**: Users may want to re-run migrations for testing or rollback scenarios.

### PostgreSQL Schema
**Issue**: PostgreSQL uses schema.table qualification, MySQL uses database.table.
**Workaround**: `DbConfig.schema` field added with `getSchemaOrDefault()` helper returning "public" for PostgreSQL.

## 11. Future Enhancements (Post-POC)

### Phase 2: Production Readiness
- [ ] TLS/SSL for all database connections
- [ ] Secrets management (HashiCorp Vault, AWS Secrets Manager)
- [ ] Job resume capability after failures
- [ ] Progress reporting via WebSocket or Server-Sent Events
- [ ] Email/Slack notifications on completion/failure

### Phase 3: Advanced Features
- [ ] Parallel table migration (multiple tables simultaneously)
- [ ] Incremental/delta migrations (not full database)
- [ ] Schema diff and validation before migration
- [ ] Dry-run mode (schema only, no data)
- [ ] Custom transformation scripts (via DistSQL)

### Phase 4: Enterprise Features
- [ ] Multi-region support with latency optimization
- [ ] Point-in-time recovery (PITR) integration
- [ ] Data masking for sensitive fields
- [ ] Compliance reporting (GDPR, HIPAA)
- [ ] Cost estimation and optimization recommendations

## 12. Success Criteria

The POC is considered successful if:

1. ✅ **Homogeneous migration** (MySQL → MySQL) completes with:
   - All tables created with correct schema
   - 100% row count match between source and target
   - No data corruption or loss

2. ✅ **Heterogeneous migration** (MySQL → PostgreSQL) completes with:
   - Schema automatically normalized for PostgreSQL
   - All data type mappings correctly applied
   - 100% row count match between source and target
   - Target schema follows PostgreSQL conventions (SERIAL instead of AUTO_INCREMENT)

3. ✅ **Automated workflow** executes end-to-end without manual intervention:
   - Single API call triggers entire migration
   - State machine transitions through all phases correctly
   - Errors are captured and reported clearly

4. ✅ **Audit trail** is maintained:
   - All generated changelogs saved to disk
   - Job metadata persisted in SQLite
   - Full request JSON stored for replay capability

5. ✅ **Documentation** enables reproduction:
   - README provides clear setup instructions
   - Architecture diagrams show component interactions
   - API examples demonstrate usage

## 13. Revision History

| Date | Version | Changes |
|------|---------|---------|
| 2025-10-21 | 1.0 | Initial POC plan |
| 2025-10-21 | 2.0 | Updated for ShardingSphere 5.5.2 syntax, added commit phase, enhanced validation, documented port mappings and dependencies |