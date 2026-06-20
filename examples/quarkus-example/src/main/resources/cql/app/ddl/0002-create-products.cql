CREATE TABLE IF NOT EXISTS products (
    id uuid PRIMARY KEY,
    name text,
    price decimal,
    created_at timestamp
);
