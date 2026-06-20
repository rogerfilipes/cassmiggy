-- Seed a couple of orders linking the seeded users and products
INSERT INTO orders (id, user_id, product_id, quantity, created_at)
VALUES (cccccccc-cccc-cccc-cccc-cccccccccccc,
        11111111-1111-1111-1111-111111111111,
        aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa, 2, '2024-01-03 00:00:00');

INSERT INTO orders (id, user_id, product_id, quantity, created_at)
VALUES (dddddddd-dddd-dddd-dddd-dddddddddddd,
        22222222-2222-2222-2222-222222222222,
        bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb, 1, '2024-01-04 00:00:00');
