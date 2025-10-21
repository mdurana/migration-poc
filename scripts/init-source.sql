-- Create sample tables and data in 'sourcedb'
USE sourcedb;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    product_name VARCHAR(100),
    amount DECIMAL(10, 2),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Insert 100 sample users
INSERT INTO users (name) VALUES ('User 1'), ('User 2'), ('User 3');
-- (Add more data as needed)
INSERT INTO orders (user_id, product_name, amount) VALUES (1, 'Widget A', 19.99), (2, 'Widget B', 100.00);