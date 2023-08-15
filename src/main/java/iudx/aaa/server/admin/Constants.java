package iudx.aaa.server.admin;

public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";

  /* Config related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_SCHEMA = "databaseSchema";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String DATABASE_POOLSIZE = "poolSize";
  public static final int DB_CONNECT_TIMEOUT = 10000;
  public static final int DB_RECONNECT_ATTEMPTS = 5;
  public static final long DB_RECONNECT_INTERVAL_MS = 10000;

  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";

  /* Response fields */
  public static final String RESP_STATUS = "status";

  /* Response title and details */
  public static final String SUCC_TITLE_CREATED_RS = "Resource Server has been created";
  public static final String SUCC_TITLE_PROVIDER_REGS = "Provider registrations";
  public static final String SUCC_TITLE_PROV_STATUS_UPDATE = "Provider status updated";

  public static final String ERR_TITLE_INVALID_DOMAIN = "Invalid URL";
  public static final String ERR_DETAIL_INVALID_DOMAIN = "The domain is invalid";

  public static final String ERR_TITLE_INVALID_PROV_REG_ID =
      "Invalid provider registration ID/ not a pending provider registration ID";

  public static final String ERR_TITLE_DUPLICATE_REQ = "Duplicate provider registration ID in request";

  public static final String ERR_TITLE_NO_COS_ADMIN_ROLE = "Invalid roles to call API - not COS Admin";
  public static final String ERR_DETAIL_NO_COS_ADMIN_ROLE =
      "Only COS Admin may call the API";

  public static final String ERR_TITLE_NOT_ADMIN = "User does not have admin role";
  public static final String ERR_DETAIL_NOT_ADMIN =
      "You are not an admin of any registered resource server";

  public static final String ERR_TITLE_DOMAIN_EXISTS = "Domains exists";
  public static final String ERR_DETAIL_DOMAIN_EXISTS =
      "A resource server exists with the given domain";

  /* SQL */
  public static final String SQL_GET_PROVIDERS_FOR_RS_BY_STATUS =
      "SELECT users.id AS \"userId\", roles.id, userinfo, resource_server.url AS \"rsUrl\" FROM users"
          + " JOIN roles ON users.id = roles.user_id"
          + " JOIN resource_server ON roles.resource_server_id = resource_server.id"
          + " WHERE roles.role = 'PROVIDER' AND roles.status = $1::role_status_enum"
          + " AND resource_server.url = ANY($2::text[])";

  public static final String SQL_UPDATE_ROLE_STATUS =
      "UPDATE roles SET status = $1::role_status_enum, updated_at = NOW() WHERE id = $2::uuid";

  public static final String SQL_GET_PENDING_PROVIDERS_BY_ID_AND_RS = 
      "SELECT users.id AS \"userId\", roles.id, userinfo, resource_server.url AS \"rsUrl\" FROM users"
          + " JOIN roles ON users.id = roles.user_id"
          + " JOIN resource_server ON roles.resource_server_id = resource_server.id"
          + " WHERE roles.role = 'PROVIDER' AND roles.status = 'PENDING' AND roles.id = ANY($1::uuid[])"
          + " AND resource_server.url = ANY($2::text[])";

  public static final String SQL_CREATE_RS_IF_NOT_EXIST =
      "INSERT INTO resource_server (name, url, owner_id, created_at, updated_at) "
          + "VALUES ($1::text, $2::text, $3::UUID, NOW(), NOW()) ON CONFLICT (url) DO NOTHING RETURNING id";
}
