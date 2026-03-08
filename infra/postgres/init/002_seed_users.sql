insert into users (id, username, password_hash, role, enabled, created_at)
values
    (gen_random_uuid(), 'admin', '$2a$10$zA3P9xVbM2k1fD9vW8uT5u7hYzq2kY0H6yQse6t0Y1xwQnH0Q6J3y', 'ADMIN', true, now()),
    (gen_random_uuid(), 'engineer', '$2a$10$zA3P9xVbM2k1fD9vW8uT5u7hYzq2kY0H6yQse6t0Y1xwQnH0Q6J3y', 'ENGINEER', true, now()),
    (gen_random_uuid(), 'viewer', '$2a$10$zA3P9xVbM2k1fD9vW8uT5u7hYzq2kY0H6yQse6t0Y1xwQnH0Q6J3y', 'VIEWER', true, now())
on conflict (username) do nothing;
