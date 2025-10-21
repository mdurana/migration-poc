# POC Development Plan: ShardingSphere Migration

## 1. Objective
This document outlines the plan for a Proof of Concept (POC) to validate the core workflow of a database migration tool.

The primary goal is to prove the functional correctness of using **Liquibase** for automated schema generation and **ShardingSphere-Proxy (DistSQL)** for data migration in a homogeneous environment.

## 2. Tech Stack

* **Runtime:** Java 25, Spring Boot 3.5.6
* **Schema Migration:** Liquibase 4.27.0
* **Data Migration:** Apache ShardingSphere-Proxy 5.5.2 (controlled via DistSQL)
* **Build:** Apache Maven (multi-module)
* **Containerization:** Docker Compose
* **Metadata (Orchestrator):** SQLite
* **Metadata (ShardingSphere):** ZooKeeper

## 3. Architecture
The POC consists of 5 containerized services managed by `docker-compose.yml`:
1.  **`orchestrator`**: A Spring Boot application containing the state machine and API. It acts as the "brain" and runs all tasks.
2.  **`source-db`**: A MySQL 8 database, pre-populated with schema and data.
3.  **`target-db`**: An empty MySQL 8 database.
4.  **`shardingsphere-proxy`**: The data migration engine. It connects to both databases.
5.  **`zookeeper`**: The metadata store required by ShardingSphere for clustering and migration job state.

## 4. Core Workflow (State Machine)
The entire migration is triggered by a single `POST /job` API call. The `JobService` then executes the following states asynchronously:

1.  **`PENDING`**: The job is created in the SQLite DB.
2.  **`SCHEMA_GENERATING`**: The `SchemaExecutor` connects to the `source-db` and runs `liquibase generate-changelog`.
3.  **`SCHEMA_GENERATE_FAILED`**: (Error state)
4.  **`SCHEMA_APPLYING`**: The `SchemaExecutor` connects to the `target-db` and runs `liquibase update` using the changelog generated in the previous step.
5.  **`SCHEMA_FAILED`**: (Error state)
6.  **`DATA_CONFIGURING`**: The `DataExecutor` sends DistSQL (`REGISTER...`, `CREATE...`) to the ShardingSphere-Proxy.
7.  **`DATA_CONFIG_FAILED`**: (Error state)
8.  **`DATA_RUNNING`**: The `DataExecutor` sends `START MIGRATION JOB` and begins polling the `CHECK...` status. This state covers both the **inventory (bulk)** load and the **incremental (CDC)** phase.
9.  **`DATA_FAILED`**: (Error state)
10. **`VALIDATING`**: The `ValidationExecutor` runs `SELECT COUNT(*)` on all tables on both source and target and compares the results.
11. **`VALIDATION_FAILED`**: (Error state)
12. **`DONE`**: The job is complete and successful.

## 5. Key Artifacts
* **Externalized Changelog**: The Liquibase changelog (`.xml`) generated during `SCHEMA_GENERATING` is saved to the host machine in the `/generated-schema` directory for audit and review.
* **Logs**: All state transitions and progress are logged to `stdout` from the `orchestrator` container.

## 6. Out of Scope (Explicitly)
* **Security**: All connections use plain-text passwords and disable SSL.
* **VLDB (Very Large Databases)**: This POC proves *functional* correctness, not performance at scale.
* **Heterogeneous Migration**: The flow is MySQL -> MySQL only.
* **High Availability / Resiliency**: If the `orchestrator` container crashes, the job stops and must be manually restarted.