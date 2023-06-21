-- We're using VACUUM and REINDEX, these can't be put in a transaction
-- Hence we **manually** make a transaction in the migration
-- and use the V8__Make_User_ID_same_as_Keycloak_ID.sql.conf script config file to 
-- tell Flyway to turn off it's default transactions **just for this migration**

BEGIN;

SET session_replication_role='replica';

UPDATE access_requests SET (owner_id) = (SELECT keycloak_id FROM users WHERE access_requests.owner_id = users.id);
UPDATE access_requests SET (user_id) = (SELECT keycloak_id FROM users WHERE access_requests.user_id = users.id);

UPDATE apd_policies SET (owner_id) = (SELECT keycloak_id FROM users WHERE apd_policies.owner_id = users.id);

UPDATE delegations SET (owner_id) = (SELECT keycloak_id FROM users WHERE delegations.owner_id = users.id);
UPDATE delegations SET (user_id) = (SELECT keycloak_id FROM users WHERE delegations.user_id = users.id);

UPDATE resource SET (provider_id) = (SELECT keycloak_id FROM users WHERE resource.provider_id = users.id);

UPDATE resource_group SET (provider_id) = (SELECT keycloak_id FROM users WHERE resource_group.provider_id = users.id);

UPDATE resource_server_admins SET (admin_id) = (SELECT keycloak_id FROM users WHERE resource_server_admins.admin_id = users.id);

UPDATE policies SET (user_id) = (SELECT keycloak_id FROM users WHERE policies.user_id = users.id);
UPDATE policies SET (owner_id) = (SELECT keycloak_id FROM users WHERE policies.owner_id = users.id);

UPDATE apds SET (owner_id) = (SELECT keycloak_id FROM users WHERE apds.owner_id = users.id);

UPDATE resource_server SET (owner_id) = (SELECT keycloak_id FROM users WHERE resource_server.owner_id = users.id);

UPDATE user_clients SET (user_id) = (SELECT keycloak_id FROM users WHERE user_clients.user_id = users.id);

UPDATE roles SET (user_id) = (SELECT keycloak_id FROM users WHERE roles.user_id = users.id);

UPDATE users SET id = keycloak_id;

SET session_replication_role='origin';
COMMIT;

-- Since this is a big updated to almost all tables, we use VACUUM to 'clean up' the DB, ANALYZE to update the query planner
-- and REINDEX to recreate the indices (since primary keys have been updated)
VACUUM ANALYZE;
REINDEX SCHEMA ${flyway:defaultSchema};
