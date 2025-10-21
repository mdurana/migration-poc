-- Set the database to use
USE sourcedb;

-- Drop tables in reverse order of dependency to avoid foreign key errors
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;

--
-- Create users table
-- We add 'email', 'status' (TINYINT), and 'registered_at' (DATETIME)
-- to test heterogeneous type mapping.
--
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    `status` TINYINT DEFAULT 1 COMMENT '1=Active, 0=Inactive, 2=Pending',
    registered_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

--
-- Create orders table
-- We add 'quantity', 'order_date', and 'notes' (TEXT)
-- to test more data types.
--
CREATE TABLE orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    product_name VARCHAR(100),
    quantity INT DEFAULT 1,
    amount DECIMAL(10, 2),
    order_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

--
-- Note: All INSERT statements have been removed.
-- Data will be loaded by the 'scripts/populate-data.py' script.
--
COMMIT;