package iudx.aaa.server.registration;

/**
 * Constants for Registration service for SQL queries, URNs, responses and other values.
 */

public class Constants {

  public static final String COMPOSE_FAILURE = "COMPOSE_FAILURE";
  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  public static final String NIL_PHONE = "0000000000";
  public static final String DEFAULT_CLIENT = "default";
  public static final String NO_ORG_CHECK = "NO_ORG_CHECK";
  public static final String EMAIL_HASH_ALG = "SHA-1";

  /* Config related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String DATABASE_POOLSIZE = "poolSize";
  public static final int DB_CONNECT_TIMEOUT = 10000;

  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";

  /* bcrypt parameters TODO move to common constants file */
  public static final int BCRYPT_LOG_COST = 12;
  public static final int BCRYPT_SALT_LEN = 16;

  /* Response fields */
  public static final String RESP_CLIENT_NAME = "client";
  public static final String RESP_CLIENT_ID = "clientId";
  public static final String RESP_CLIENT_SC = "clientSecret";
  public static final String RESP_CLIENT_ARR = "clients";
  public static final String RESP_EMAIL = "email";

  /* URNs */
  public static final String URN_SUCCESS = "urn:dx:as:Success";
  public static final String URN_MISSING_INFO = "urn:dx:as:MissingInformation";
  public static final String URN_INVALID_INPUT = "urn:dx:as:InvalidInput";
  public static final String URN_ALREADY_EXISTS = "urn:dx:as:AlreadyExists";

  /* Response title and details */
  public static final String SUCC_TITLE_CREATED_USER = "User profile has been created";
  public static final String PROVIDER_PENDING_MESG = ", Provider registration is pending approval";

  public static final String ERR_TITLE_ORG_ID_REQUIRED = "Missing Organization ID";
  public static final String ERR_DETAIL_ORG_ID_REQUIRED =
      "Organization ID is required for Provider/Delegate registration";

  public static final String ERR_TITLE_USER_EXISTS = "User exists";
  public static final String ERR_DETAIL_USER_EXISTS = "User has an existing user profile";

  public static final String ERR_TITLE_USER_NOT_KC = "User not on identity server";
  public static final String ERR_DETAIL_USER_NOT_KC = "User has not registered on identity server";

  public static final String ERR_TITLE_ORG_NO_EXIST = "Organization does not exist";
  public static final String ERR_DETAIL_ORG_NO_EXIST =
      "Organization ID does not correspond to an organization";

  public static final String ERR_TITLE_ORG_NO_MATCH = "User does not belong to organization";
  public static final String ERR_DETAIL_ORG_NO_MATCH =
      "User's email domain does not match the organization domain";

  /* SQL queries */
  public static final String SQL_FIND_USER_BY_KC_ID =
      "SELECT * FROM test.users WHERE keycloak_id = $1";
  public static final String SQL_FIND_ORG_BY_ID =
      "SELECT * FROM test.organizations WHERE id = $1::uuid";

  public static final String SQL_CREATE_USER =
      "INSERT INTO test.users (phone, organization_id, email_hash, keycloak_id, "
          + "created_at, updated_at) VALUES ($1::text, $2::uuid, $3::text, $4::uuid, "
          + "NOW(), NOW()) " + " RETURNING id";

  public static final String SQL_CREATE_ROLE =
      "INSERT INTO test.roles (user_id, role, status, created_at, updated_at)"
          + " VALUES ($1::uuid, $2::test.role_enum, $3::test.role_status_enum, NOW(), NOW())";

  public static final String SQL_CREATE_CLIENT = "INSERT INTO test.user_clients"
      + " (user_id, client_id, client_secret, client_name, created_at, updated_at)"
      + " VALUES ($1::uuid, $2::uuid, $3::text, $4::text, NOW(), NOW())";

  public static final String SQL_GET_ORG_DETAILS =
      "SELECT name, url FROM test.organizations WHERE id = $1::uuid";

  public static final String SQL_GET_ALL_ORGS = "SELECT id, name, url FROM test.organizations";

  public static final String SQL_GET_REG_ROLES =
      "SELECT role FROM test.roles WHERE user_id = $1::uuid";

  public static final String SQL_GET_CLIENTS_FORMATTED =
      "SELECT client_name as \"clientName\", client_id as \"clientId\" "
          + " FROM test.user_clients WHERE user_id = $1::uuid";

  public static final String SQL_GET_ORG_DETAILS_BY_USER_ID =
      "SELECT name, url FROM test.organizations JOIN test.users"
          + " ON users.organization_id = organizations.id WHERE users.id = $1::uuid";
}
