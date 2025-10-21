# Migration POC

This is a Proof of Concept (POC) for a database migration tool using ShardingSphere-Proxy and Liquibase.

**This POC is NOT secure.** It uses plain-text passwords and disables SSL. It is for functional testing in an isolated sandbox ONLY.

## Components

* **Orchestrator**: A Spring Boot app that runs the migration state machine.
* **ShardingSphere-Proxy**: The workhorse that performs the data migration via DistSQL.
* **ZooKeeper**: Metadata store required by ShardingSphere.
* **source-db**: A MySQL 8 source database.
* **target-db**: A MySQL 8 target database.

## How to Run

1.  **Prep Source DB**:
    * (Optional) Modify `scripts/init-source.sql`. This script creates the sample **schema and data** in the source database when it first starts.

2.  **Start Services**:
    ```bash
    docker-compose up --build -d
    ```

3.  **Start Migration Job**:
    * Open `config/job-template.json` and verify the settings (e.g., table names).
    * Send the job to the orchestrator:
    ```bash
    curl -X POST http://localhost:8080/job \
         -H "Content-Type: application/json" \
         -d @config/job-template.json
    ```

4.  **Monitor Logs**:
    ```bash
    docker-compose logs -f orchestrator
    ```
    
    You will see the state machine progress:
    `PENDING` -> `SCHEMA_GENERATING` -> `SCHEMA_APPLYING` -> `DATA_CONFIGURING` -> `DATA_RUNNING` -> `VALIDATING` -> `DONE`

5.  **Test CDC**:
    * While the job is in `DATA_RUNNING`, connect to the source DB and make changes.
    * `docker-compose exec source-db mysql -u root -psource_password sourcedb`
    * `INSERT INTO users (name) VALUES ('cdc-test');`

6.  **Verify Schema Artifact (Externalized Output)**:
    * Check your host machine's `generated-schema/` directory.
    * You will find the auto-generated Liquibase changelog (e.g., `job-1-changelog.xml`) that was applied to the target.

7.  **Verify Data**:
    * Once the log shows `DONE`, connect to the target DB.
    * `docker-compose exec target-db mysql -u root -ptarget_password targetdb`
    * `SELECT * FROM users;`
    * Verify the "cdc-test" user exists and all other data is present.

8.  **Clean Up**:
    ```bash
    docker-compose down -v
    ```