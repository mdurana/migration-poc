# POC Development Plan: ShardingSphere Migration

## 1. Objective
This document outlines the plan for a Proof of Concept (POC) to validate the core workflow of a database migration tool.

The primary goal is to prove the functional correctness of using **Liquibase** for automated schema generation and **ShardingSphere-Proxy (DistSQL)** for data migration in both **homogeneous (MySQL $\rightarrow$ MySQL)** and **heterogeneous (MySQL $\rightarrow$ PostgreSQL)** environments.

It also validates an *automated* schema normalization step for basic heterogeneous migrations, removing the need for manual intervention.

## 2. Tech Stack
* **Runtime:** Java 25 (with `--enable-preview` features enabled), Spring Boot 3.5.6
* **Schema Migration:** Liquibase 4.31.1
* **Data Migration:** Apache ShardingSphere-Proxy 5.5.2
* **Build:** Apache Maven (multi-module) with `maven-compiler-plugin:3.14.0`
* **Containerization:** Docker Compose
* **Data Generation:** Python (`mysql-connector-python`)
* **Metadata (Orchestrator):** SQLite 3.49.1.0 (with Hibernate 6.6.29.Final)
* **Metadata (ShardingSphere):** ZooKeeper

## 3. Architecture
The POC consists of 6 containerized services managed by `docker-compose.yml`, plus one local script:

1.  **`orchestrator`**: A Spring Boot application (Maven module) containing the state machine and API. It acts as the "brain" and runs all tasks.
2.  **`source-db`**: A MySQL 8 database, initialized with an empty schema.
3.  **`target-db`**: An empty MySQL 8 database (for homogeneous tests).
4.  **`target-db-pg`**: An empty PostgreSQL 15 database (for heterogeneous tests).
5.  **`shardingsphere-proxy`**: The data migration engine. It connects to both databases.
6.  **`zookeeper`**: The metadata store required by ShardingSphere for migration job state.
7.  **`scripts/populate-data.py`**: A local Python script used to generate and load a large (6,000+ row) test dataset into the `source-db` *after* it starts.

## 4. Core Workflow (State Machine)
The entire migration is triggered by a single `POST /job` API call. The `JobService` then executes the following states asynchronously:

1.  **`PENDING`**: The job is created in the SQLite DB.
2.  **`SCHEMA_GENERATING`**: The `SchemaExecutor` connects to the `source-db` and runs `liquibase generate-changelog` to create a raw XML changelog.
3.  **`SCHEMA_GENERATE_FAILED`**: (Error state)
4.  **`SCHEMA_NORMALIZING`**: **(Conditional)** If a heterogeneous migration is detected, this automated step parses the raw XML and translates source-specific types (e.g., MySQL `TINYINT`, `AUTO_INCREMENT`) to target-specific types (e.g., PostgreSQL `SMALLINT`, `SERIAL`).
5.  **`SCHEMA_NORMALIZE_FAILED`**: (Error state)
6.  **`SCHEMA_APPLYING`**: The `SchemaExecutor` connects to the `target-db` and runs `liquibase update` using the appropriate changelog (the raw one for homogeneous, the `.normalized.xml` for heterogeneous).
7.  **`SCHEMA_FAILED`**: (Error state)
8.  **`DATA_CONFIGURING`**: The `DataExecutor` sends DistSQL (`REGISTER...`, `CREATE...`) to the ShardingSphere-Proxy, including any `dataTypeMappings` for the data-level translation.
9.  **`DATA_CONFIG_FAILED`**: (Error state)
10. **`DATA_RUNNING`**: The `DataExecutor` sends `START MIGRATION JOB` and begins polling the `CHECK...` status. This state covers both the **inventory (bulk)** load and the **incremental (CDC)** phase.
11. **`DATA_FAILED`**: (Error state)
12. **`VALIDATING`**: The `ValidationExecutor` runs `SELECT COUNT(*)` on all tables on both source and target and compares the results.
13. **`VALIDATION_FAILED`**: (Error state)
14. **`DONE`**: The job is complete and successful.

## 5. Key Artifacts
* **Externalized Changelogs**: All generated changelogs are saved to the host machine in the `/generated-schema` directory for audit.
* In a heterogeneous migration, **two** changelogs are created: the raw `job-X-changelog.xml` and the auto-translated `job-X-changelog.normalized.xml` that was actually applied.
* **Logs**: All state transitions and progress are logged to `stdout` from the `orchestrator` container.

## 6. Out of Scope (Explicitly)
* **Security**: All connections use plain-text passwords and disable SSL.
* **Advanced Normalization**: The automated normalization step **only** handles basic data types and auto-increment. It will fail on complex triggers, stored procedures, or functions.
* **VLDB (Very Large Databases)**: This POC does not test true VLDBs (multi-terabyte scale), but it *does* validate the workflow with a non-trivial dataset (6,000+ rows) loaded from the Python script.
* **High Availability / Resiliency**: If the `orchestrator` container crashes, the job stops and must be manually restarted (by re-running the `docker-compose down -v` and starting over).