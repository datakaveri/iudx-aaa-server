CREATE VIEW resource_server_view AS
SELECT
  id,
  owner_id,
  url,
  created_at,
  updated_at
FROM
  resource_server
UNION
SELECT
  resource_server_admins.resource_server_id,
  resource_server_admins.admin_id,
  resource_server.url,
  resource_server_admins.created_at,
  resource_server_admins.updated_at
FROM
  resource_server_admins
  JOIN resource_server ON resource_server_admins.resource_server_id = resource_server.id;

ALTER VIEW resource_server_view OWNER TO ${flyway:user};

GRANT SELECT ON resource_server_view TO ${authUser};
