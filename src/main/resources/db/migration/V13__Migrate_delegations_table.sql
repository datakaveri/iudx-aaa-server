-- add new role_id column
ALTER TABLE delegations ADD COLUMN role_id uuid REFERENCES roles (id) DEFAULT NULL;

-- for all existing active delegations, create new rows with the correct role_id
WITH new_delegations_data AS (
    SELECT
        delegations.user_id,
        delegations.owner_id,
        delegations.resource_server_id,
        roles.id,
        delegations.status,
        delegations.created_at,
        delegations.updated_at
    FROM
        delegations
        JOIN roles ON delegations.resource_server_id = roles.resource_server_id
            AND delegations.owner_id = roles.user_id
    WHERE
        delegations.status = 'ACTIVE'
        AND roles.status = 'APPROVED'
        AND roles.role = 'PROVIDER')
INSERT INTO delegations (user_id, owner_id, resource_server_id, role_id, status, created_at, updated_at) (
    SELECT
        *
    FROM
        new_delegations_data);

-- remove owner_id and resource_server_id since we get it from the role_id
ALTER TABLE delegations DROP COLUMN owner_id;
ALTER TABLE delegations DROP COLUMN resource_server_id;

-- remove all original rows that do not have a role_id (rows with status = 'DELETED' also go away)
DELETE FROM delegations WHERE role_id IS NULL;

-- remove the default null and add not null constraint
ALTER TABLE delegations ALTER COLUMN role_id DROP DEFAULT;
ALTER TABLE delegations ALTER COLUMN role_id SET NOT NULL;
