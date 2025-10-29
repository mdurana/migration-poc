-- This script is for PostgreSQL.
-- The 'USE sourcedb;' command is not needed. In the Docker context,
-- this script runs automatically against the database specified by POSTGRES_DB.

-- Drop tables in reverse order of dependency to avoid foreign key errors
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;

--
-- Create users table
-- We add 'email', 'status' (SMALLINT), and 'registered_at' (TIMESTAMP)
-- to test heterogeneous type mapping.
--
CREATE TABLE users (
    -- SERIAL is the PostgreSQL equivalent of AUTO_INCREMENT
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    -- TINYINT does not exist in Postgres, use SMALLINT
    status SMALLINT DEFAULT 1,
    -- DATETIME is TIMESTAMP in Postgres
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Comments are added separately in PostgreSQL
COMMENT ON COLUMN users.status IS '1=Active, 0=Inactive, 2=Pending';

--
-- Create orders table
-- We add 'quantity', 'order_date', and 'notes' (TEXT)
-- to test more data types.
--
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    user_id INT,
    product_name VARCHAR(100),
    quantity INT DEFAULT 1,
    amount DECIMAL(10, 2),
    -- DATETIME is TIMESTAMP in Postgres
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

--
-- Note: All INSERT statements have been removed.
-- Data will be loaded by the 'scripts/populate-data-pg.py' script.
--
-- COMMIT is not needed; DDL statements are transactional and auto-commit.