import mysql.connector
import random
from datetime import datetime, timedelta

# --- Configuration ---
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'source_password',
    'database': 'sourcedb'
}
NUM_USERS = 1000
NUM_ORDERS = 5000
# ---------------------

def get_random_date(start, end):
    """Generate a random datetime between two datetimes."""
    delta = end - start
    int_delta = (delta.days * 24 * 60 * 60) + delta.seconds
    random_second = random.randrange(int_delta)
    return start + timedelta(seconds=random_second)

def populate_users(cursor):
    """Generate and insert users."""
    print(f"Generating {NUM_USERS} users...")
    users_to_insert = []
    start_date = datetime(2023, 1, 1)
    end_date = datetime.now()
    
    for i in range(1, NUM_USERS + 1):
        name = f"User {i}"
        email = f"user{i}@example.com"
        status = random.choice([0, 1, 2])
        reg_date = get_random_date(start_date, end_date)
        users_to_insert.append((name, email, status, reg_date))

    sql = "INSERT INTO users (name, email, `status`, registered_at) VALUES (%s, %s, %s, %s)"
    cursor.executemany(sql, users_to_insert)
    print("Users populated successfully.")

def populate_orders(cursor):
    """Generate and insert orders."""
    print(f"Generating {NUM_ORDERS} orders...")
    orders_to_insert = []
    products = ['Super Widget', 'Mega Gadget', 'Standard Sprocket', 'Microchip', 'Deluxe Hammer']
    start_date = datetime(2023, 6, 1)
    end_date = datetime.now()

    for _ in range(NUM_ORDERS):
        user_id = random.randint(1, NUM_USERS)
        product = random.choice(products)
        quantity = random.randint(1, 10)
        amount = round(random.uniform(5.99, 499.99), 2)
        order_date = get_random_date(start_date, end_date)
        notes = None
        if random.random() < 0.1: # 10% chance of a note
            notes = f"Order note for user {user_id}. Handle with care."
            
        orders_to_insert.append((user_id, product, quantity, amount, order_date, notes))

    sql = "INSERT INTO orders (user_id, product_name, quantity, amount, order_date, notes) VALUES (%s, %s, %s, %s, %s, %s)"
    cursor.executemany(sql, orders_to_insert)
    print("Orders populated successfully.")

def main():
    try:
        print("Connecting to source database...")
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Clear existing data
        cursor.execute("SET FOREIGN_KEY_CHECKS = 0;")
        cursor.execute("TRUNCATE TABLE orders;")
        cursor.execute("TRUNCATE TABLE users;")
        cursor.execute("SET FOREIGN_KEY_CHECKS = 1;")
        print("Cleared existing data.")
        
        populate_users(cursor)
        populate_orders(cursor)
        
        conn.commit()
        
        # Verify
        cursor.execute("SELECT COUNT(*) FROM users")
        user_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM orders")
        order_count = cursor.fetchone()[0]
        
        print("\n--- Verification ---")
        print(f"Total Users:  {user_count}")
        print(f"Total Orders: {order_count}")
        print("--------------------")

    except mysql.connector.Error as err:
        print(f"Error: {err}")
    finally:
        if 'conn' in locals() and conn.is_connected():
            cursor.close()
            conn.close()
            print("Database connection closed.")

if __name__ == "__main__":
    main()