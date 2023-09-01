-- delete all admin, trustee and delegate roles
DELETE FROM roles WHERE role = 'ADMIN' OR role = 'TRUSTEE' OR role = 'DELEGATE';

-- modify the roles enum to remove admin, trustee, delegate
CREATE TYPE new_role_enum AS ENUM (
    'PROVIDER',
    'CONSUMER'
);

ALTER TABLE roles ALTER COLUMN role TYPE new_role_enum USING role::text::new_role_enum;

DROP TYPE role_enum;

ALTER TYPE new_role_enum RENAME TO role_enum;

-- delete all pending, rejected providers
DELETE FROM roles WHERE role = 'PROVIDER' AND (status = 'PENDING' OR status = 'REJECTED');

-- add new resource_server_id column to roles table
ALTER TABLE roles ADD COLUMN resource_server_id uuid REFERENCES resource_server (id) DEFAULT NULL;

-- for all existing roles, add all existing resource servers - this is why we use a cross join here
WITH new_roles_data AS (
    SELECT
        user_id,
        role,
        status,
        roles.created_at,
        roles.updated_at,
        resource_server.id AS resource_server_id
    FROM
        roles
        CROSS JOIN resource_server)
    INSERT INTO roles (user_id, role, status, created_at, updated_at, resource_server_id) (
        SELECT
            *
        FROM
            new_roles_data);

-- remove the rows where the nulls were there
DELETE FROM roles WHERE resource_server_id IS NULL;

-- remove the default null and add not null constraint
ALTER TABLE roles ALTER COLUMN resource_server_id DROP DEFAULT;
ALTER TABLE roles ALTER COLUMN resource_server_id SET NOT NULL;
