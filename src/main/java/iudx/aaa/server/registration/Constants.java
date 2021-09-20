package iudx.aaa.server.registration;

import iudx.aaa.server.apiserver.Schema;

/**
 * Constants for Registration service for SQL queries, URNs, responses and other values.
 */

public class Constants {

  public static final String COMPOSE_FAILURE = "COMPOSE_FAILURE";
  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  public static final String NIL_PHONE = "0000000000";
  public static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";
  public static final String DEFAULT_CLIENT = "default";
  public static final String NO_ORG_CHECK = "NO_ORG_CHECK";

  /* Config related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String DATABASE_POOLSIZE = "poolSize";
  public static final int DB_CONNECT_TIMEOUT = 10000;

  public static final Schema DB_SCHEMA = Schema.INSTANCE; 

  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";

  /* bcrypt parameters TODO move to common constants file */
  public static final int BCRYPT_LOG_COST = 12;
  public static final int BCRYPT_SALT_LEN = 16;

  /* Response fields */
  public static final String RESP_CLIENT_NAME = "clientName";
  public static final String RESP_CLIENT_ID = "clientId";
  public static final String RESP_CLIENT_SC = "clientSecret";
  public static final String RESP_CLIENT_ARR = "clients";
  public static final String RESP_EMAIL = "email";
  public static final String RESP_PHONE = "phone";
  public static final String RESP_ORG = "organization";

  /* URNs */
  public static final String URN_SUCCESS = "urn:dx:as:Success";
  public static final String URN_MISSING_INFO = "urn:dx:as:MissingInformation";
  public static final String URN_INVALID_INPUT = "urn:dx:as:InvalidInput";
  public static final String URN_INVALID_ROLE = "urn:dx:as:InvalidRole";
  public static final String URN_ALREADY_EXISTS = "urn:dx:as:AlreadyExists";

  /* Response title and details */
  public static final String SUCC_TITLE_CREATED_USER = "User profile has been created";
  public static final String PROVIDER_PENDING_MESG = ", Provider registration is pending approval";
  public static final String SUCC_TITLE_USER_READ = "User details";
  public static final String SUCC_TITLE_USER_FOUND = "User found";
  public static final String SUCC_TITLE_ORG_READ = "Organizations";
  public static final String SUCC_TITLE_UPDATED_USER_ROLES = "Registered for requested roles";

  public static final String ERR_TITLE_ROLE_EXISTS = "Already registered for requested role";
  public static final String ERR_DETAIL_ROLE_EXISTS = "You have already registered as ";

  public static final String ERR_TITLE_NO_USER_PROFILE = "User profile does not exist";
  public static final String ERR_DETAIL_NO_USER_PROFILE = "Please register to create user profile";

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

  public static final String ERR_TITLE_USER_NOT_FOUND = "User not found";
  public static final String ERR_DETAIL_USER_NOT_FOUND =
      "A user with given email and role not found";

  public static final String ERR_TITLE_SEARCH_USR_INVALID_ROLE =
      "User does not have required role to search for user";
  public static final String ERR_DETAIL_SEARCH_USR_INVALID_ROLE =
      "Must have provider/admin roles or be an auth delegate";

  /* SQL queries */
  public static final String SQL_FIND_USER_BY_KC_ID =
      "SELECT * FROM " + DB_SCHEMA + ".users WHERE keycloak_id = $1";
  public static final String SQL_FIND_ORG_BY_ID =
      "SELECT * FROM " + DB_SCHEMA + ".organizations WHERE id = $1::uuid";

  public static final String SQL_CREATE_USER =
      "INSERT INTO " + DB_SCHEMA + ".users (phone, organization_id, email_hash, keycloak_id, "
          + "created_at, updated_at) VALUES ($1::text, $2::uuid, $3::text, $4::uuid, "
          + "NOW(), NOW()) " + " RETURNING id";

  public static final String SQL_CREATE_ROLE = "INSERT INTO " + DB_SCHEMA
      + ".roles (user_id, role, status, created_at, updated_at)" + " VALUES ($1::uuid, $2::"
      + DB_SCHEMA + ".role_enum, $3::" + DB_SCHEMA + ".role_status_enum, NOW(), NOW())";

  public static final String SQL_CREATE_CLIENT = "INSERT INTO " + DB_SCHEMA + ".user_clients"
      + " (user_id, client_id, client_secret, client_name, created_at, updated_at)"
      + " VALUES ($1::uuid, $2::uuid, $3::text, $4::text, NOW(), NOW())";

  public static final String SQL_GET_ORG_DETAILS =
      "SELECT name, url FROM " + DB_SCHEMA + ".organizations WHERE id = $1::uuid";

  public static final String SQL_GET_ALL_ORGS =
      "SELECT id, name, url FROM " + DB_SCHEMA + ".organizations";

  public static final String SQL_GET_REG_ROLES =
      "SELECT role FROM " + DB_SCHEMA + ".roles WHERE user_id = $1::uuid";

  public static final String SQL_GET_CLIENTS_FORMATTED =
      "SELECT client_name as \"clientName\", client_id as \"clientId\" " + " FROM " + DB_SCHEMA
          + ".user_clients WHERE user_id = $1::uuid";

  public static final String SQL_UPDATE_ORG_ID = "UPDATE " + DB_SCHEMA
      + ".users SET organization_id = $1::uuid WHERE organization_id IS NULL AND id = $2::uuid";

  public static final String SQL_GET_PHONE_JOIN_ORG =
      "SELECT users.phone, name, url FROM " + DB_SCHEMA + ".organizations RIGHT JOIN " + DB_SCHEMA
          + ".users" + " ON users.organization_id = organizations.id WHERE users.id = $1::uuid";

  public static final String SQL_GET_KC_ID_FROM_ARR =
      "SELECT id, keycloak_id FROM " + DB_SCHEMA + ".users WHERE id = ANY($1::uuid[])";

  public static final String SQL_GET_USER_ID_ORG = "SELECT users.id, organizations.name, url FROM "
      + DB_SCHEMA + ".users" + " JOIN " + DB_SCHEMA
      + ".organizations ON users.organization_id = organizations.id WHERE keycloak_id = $1::uuid";

  public static final String SQL_GET_UID_ORG_ID_CHECK_ROLE =
      "SELECT users.id, users.organization_id FROM " + DB_SCHEMA + ".users" + " JOIN " + DB_SCHEMA
          + ".roles ON users.id = roles.user_id"
          + " WHERE users.keycloak_id = $1::uuid AND roles.role = $2::" + DB_SCHEMA
          + ".role_enum AND roles.status = 'APPROVED'";
}
