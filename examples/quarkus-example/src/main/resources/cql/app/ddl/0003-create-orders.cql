CREATE TABLE IF NOT EXISTS orders (
    id uuid PRIMARY KEY,
    user_id uuid,
    product_id uuid,
    quantity int,
    created_at timestamp
);
