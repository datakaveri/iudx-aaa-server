package iudx.aaa.server.registration;

/**
 * Constants for Registration service for SQL queries, URNs, responses and other values.
 */

public class Constants {

  public static final String NIL_PHONE = "0000000000";
  public static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";
  public static final String DEFAULT_CLIENT = "default";

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
  public static final String CONFIG_AUTH_URL = "authServerDomain";
  public static final String CONFIG_OMITTED_SERVERS = "serversOmittedFromRevoke";
  
  public static final int CLIENT_SECRET_BYTES = 20; 

  /* Response fields */
  public static final String RESP_CLIENT_NAME = "clientName";
  public static final String RESP_CLIENT_ID = "clientId";
  public static final String RESP_CLIENT_SC = "clientSecret";
  public static final String RESP_CLIENT_ARR = "clients";
  public static final String RESP_EMAIL = "email";
  public static final String RESP_PHONE = "phone";

  /* Response title and details */
  public static final String SUCC_TITLE_ADDED_ROLES = "Requested roles have been added";
  public static final String PROVIDER_PENDING_MESG = ", Provider registrations are pending approval by resource server admin";
  public static final String SUCC_TITLE_USER_READ = "User details";
  public static final String SUCC_TITLE_USER_FOUND = "User found";
  public static final String SUCC_TITLE_RS_READ = "Resource Servers";
  public static final String SUCC_TITLE_REGEN_CLIENT_SECRET = "Regenerated client secret for requested client ID";
  public static final String SUCC_TITLE_CREATED_DEFAULT_CLIENT = "Created default client credentials";

  public static final String ERR_TITLE_NO_APPROVED_ROLES = "User does not have any roles";
  public static final String ERR_DETAIL_NO_APPROVED_ROLES =
      "Please add roles or wait for approval of provider roles";

  public static final String ERR_TITLE_ROLE_FOR_RS_EXISTS = "Role for resource server already exists";
  public static final String ERR_DETAIL_PROVIDER_FOR_RS_EXISTS =
      "Provider role exists for the requested resource servers ";
  public static final String ERR_DETAIL_CONSUMER_FOR_RS_EXISTS =
      "Consumer role exists for the requested resource servers ";
  
  public static final String ERR_CONTEXT_EXISTING_ROLE_FOR_RS = "existingRs";

  public static final String ERR_TITLE_USER_EXISTS = "User exists";
  public static final String ERR_DETAIL_USER_EXISTS = "User has an existing user profile";

  public static final String ERR_TITLE_USER_NOT_KC = "User not on identity server";
  public static final String ERR_DETAIL_USER_NOT_KC = "User has not registered on identity server";

  public static final String ERR_TITLE_RS_NO_EXIST = "Resource server does not exist";
  public static final String ERR_DETAIL_RS_NO_EXIST =
      "Resource server URL does not correspond to a registered resource server ";
  public static final String ERR_CONTEXT_NOT_FOUND_RS_URLS = "urls";

  public static final String ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS = "Pending/Rejected provider registration for resource server";
  public static final String ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS =
      "User has a pending/rejected provider registration for the requested resource servers";

  public static final String ERR_TITLE_NOT_TRUSTEE = "User does not have trustee role";
  public static final String ERR_DETAIL_NOT_TRUSTEE =
      "Only trustees may call this API. You are not a trustee of any registered and active APD";
  
  public static final String ERR_TITLE_USER_NOT_FOUND = "User not found";
  public static final String ERR_DETAIL_USER_NOT_FOUND =
      "A user with given email/user ID, role and resource server not found";

  public static final String ERR_TITLE_INVALID_CLI_ID = "Invalid client ID";
  public static final String ERR_DETAIL_INVALID_CLI_ID = "Requested client ID not found";

  public static final String ERR_TITLE_DEFAULT_CLIENT_EXISTS = "Default client credentials exists";
  public static final String ERR_DETAIL_DEFAULT_CLIENT_EXISTS =
      "The default client credentials have already been created. "
      + "If you have forgotten your client secret, please use the regenerate client secret API";
  
  public static final String ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK =
      "Some email IDs have not been registered at UAC";

  /* SQL queries */
  public static final String SQL_CREATE_ROLE =
      "INSERT INTO roles (user_id, role, resource_server_id, status"
          + ", created_at, updated_at) VALUES ($1::uuid, $2::role_enum, $3::uuid, $4::role_status_enum"
          + ", NOW(), NOW())";

  /**
   * Used for inserting roles with the role IDs during tests. Makes things easier.
   */
  public static final String TEST_SQL_CREATE_ROLE_WITH_ID =
      "INSERT INTO roles (id, user_id, role, resource_server_id, status"
          + ", created_at, updated_at) VALUES ($1::uuid, $2::uuid, $3::role_enum, $4::uuid"
          + ", $5::role_status_enum"
          + ", NOW(), NOW())";

  public static final String SQL_CREATE_CLIENT = "INSERT INTO user_clients"
      + " (user_id, client_id, client_secret, client_name, created_at, updated_at)"
      + " VALUES ($1::uuid, $2::uuid, $3::text, $4::text, NOW(), NOW())";

  public static final String SQL_GET_RS_IDS_BY_URL =
      "SELECT id, url FROM resource_server WHERE url = ANY($1::text[])";

  public static final String SQL_GET_ALL_RS =
      "SELECT id, name, url, owner_id FROM resource_server";

  public static final String SQL_GET_REG_ROLES =
      "SELECT role FROM roles WHERE user_id = $1::uuid";

  public static final String SQL_GET_CLIENTS_FORMATTED =
      "SELECT client_name as \"clientName\", client_id as \"clientId\" " + " FROM "
          + "user_clients WHERE user_id = $1::uuid";

  public static final String SQL_GET_PHONE =
      "SELECT phone FROM users WHERE id = $1::uuid";

  public static final String SQL_CHECK_USER_HAS_PROV_CONS_ROLE_FOR_RS =
      "SELECT EXISTS (SELECT 1 FROM roles JOIN" 
          + " resource_server ON roles.resource_server_id = resource_server.id"
          + " WHERE roles.user_id = $1::uuid AND roles.role = $2::role_enum"
          + " AND roles.status = 'APPROVED' AND resource_server.url = $3::text)";
  
  public static final String SQL_CHECK_CLIENT_ID_EXISTS =
      "SELECT EXISTS (SELECT 1 FROM user_clients WHERE client_id = $1::uuid AND user_id = $2::uuid)";
  
  public static final String SQL_CHECK_DEFAULT_CLIENT_EXISTS =
      "SELECT client_id FROM user_clients WHERE user_id = $1::uuid AND client_name = '" + DEFAULT_CLIENT + "'";
  
  public static final String SQL_GET_RS_AND_APDS_FOR_REVOKE =
      "SELECT url FROM resource_server WHERE url != ALL($1::text[]) UNION SELECT url FROM apds WHERE url != ALL($1::text[])";
  
  public static final String SQL_UPDATE_CLIENT_SECRET =
      "UPDATE user_clients SET client_secret = $1::text, updated_at = NOW() "
          + "WHERE client_id = $2::uuid AND user_id = $3::uuid";

  public static final String SQL_CREATE_USER_IF_NOT_EXISTS =
      "INSERT INTO users (id, phone, userinfo, created_at, updated_at) VALUES ($1::uuid, $2::text"
          + ",$3::jsonb, NOW(), NOW()) ON CONFLICT (id) DO NOTHING RETURNING id";
  
  public static final String SQL_CHECK_PENDING_REJECTED_PROVIDER_ROLES =
      "SELECT resource_server_id, status FROM roles WHERE role = 'PROVIDER' AND (status = 'PENDING' OR status = 'REJECTED')"
      + " AND resource_server_id = ANY($1::UUID[]) AND user_id = $2::UUID";
}
