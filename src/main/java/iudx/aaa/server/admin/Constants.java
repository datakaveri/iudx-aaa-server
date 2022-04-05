package iudx.aaa.server.admin;

public class Constants {

  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  public static final String COMPOSE_FAILURE = "COMPOSE_FAILURE";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";

  /* Config related */
  public static final String CONFIG_AUTH_URL = "authServerDomain";
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_SCHEMA = "databaseSchema";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String DATABASE_POOLSIZE = "poolSize";
  public static final int DB_CONNECT_TIMEOUT = 10000;

  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";

  /* Response fields */
  public static final String RESP_USERID = "userId";
  public static final String RESP_STATUS = "status";
  public static final String RESP_ORG = "organization";

  /* Response title and details */
  public static final String SUCC_TITLE_CREATED_ORG = "Organization has been created";
  public static final String SUCC_TITLE_PROVIDER_REGS = "Provider registrations";
  public static final String SUCC_TITLE_PROV_STATUS_UPDATE = "Provider status updated";

  public static final String ERR_TITLE_INVALID_DOMAIN = "Invalid URL";
  public static final String ERR_DETAIL_INVALID_DOMAIN = "The domain is invalid";

  public static final String ERR_TITLE_INVALID_USER =
      "Invalid User ID, not a provider/pending provider";

  public static final String ERR_TITLE_DUPLICATE_REQ = "Duplicate user ID in request";

  public static final String ERR_TITLE_NO_USER_PROFILE = "User profile does not exist";
  public static final String ERR_DETAIL_NO_USER_PROFILE = "Please register to create user profile";

  public static final String ERR_TITLE_NOT_AUTH_ADMIN = "Not admin of auth server";
  public static final String ERR_DETAIL_NOT_AUTH_ADMIN = "You are not an admin of the auth server";

  public static final String ERR_TITLE_DOMAIN_EXISTS = "Domains exists";
  public static final String ERR_DETAIL_DOMAIN_EXISTS =
      "An organization exists with the given domain";

  /* SQL */
  public static final String SQL_CREATE_ORG_IF_NOT_EXIST =
      "INSERT INTO organizations (name, url, created_at, updated_at) "
          + "VALUES ($1::text, $2::text, NOW(), NOW()) ON CONFLICT (url) DO NOTHING RETURNING id";

  public static final String SQL_CHECK_ADMIN_OF_SERVER = "SELECT id FROM " 
      + "resource_server WHERE owner_id = $1::uuid AND url = $2::text";

  public static final String SQL_GET_PROVIDERS_BY_STATUS =
      "SELECT users.id, keycloak_id, organization_id FROM users JOIN "
          + "roles ON users.id = roles.user_id "
          + "WHERE roles.role = 'PROVIDER' AND roles.status = $1::role_status_enum";

  public static final String SQL_GET_ORG_DETAILS =
      "SELECT id, name, url FROM organizations WHERE id = ANY($1::uuid[])";

  public static final String SQL_UPDATE_ROLE_STATUS =
      "UPDATE roles SET status = $1::role_status_enum, updated_at = NOW() WHERE user_id = $2::uuid";

  public static final String SQL_GET_PENDING_PROVIDERS = "SELECT users.id, keycloak_id FROM "
      + "users JOIN roles ON users.id = roles.user_id "
      + "WHERE role = 'PROVIDER' AND status = 'PENDING' AND user_id = ANY ($1::uuid[])";
}
