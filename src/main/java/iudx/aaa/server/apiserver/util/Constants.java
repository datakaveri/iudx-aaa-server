package iudx.aaa.server.apiserver.util;

public class Constants {
  // Header params
  public static final String HEADER_TOKEN = "token";
  public static final String HEADER_HOST = "Host";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_ORIGIN = "Origin";
  public static final String HEADER_REFERER = "Referer";
  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String HEADER_OPTIONS = "options";

  /* Configuration & related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String POOLSIZE = "poolSize";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTPRE_PASSWORD = "keystorePassword";
  public static final String AUTHSERVER_DOMAIN = "authServerDomain";
  public static final String KEYCLOACK_OPTIONS = "keycloakOptions";
  public static final int PG_CONNECTION_TIMEOUT = 10000;

  // API Documentation endpoint
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String ROUTE_DOC = "/apis";
  public static final String PUBLIC_KEY_ROUTE = "/auth/v1/cert";

  // Accept Headers and CORS
  public static final String MIME_APPLICATION_JSON = "application/json";
  public static final String MIME_TEXT_HTML = "text/html";

  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

  /* API Server Operations/Routes */
  public static final String CREATE_TOKEN = "post-auth-v1-token";
  public static final String TIP_TOKEN = "post-auth-v1-introspect"; 
  public static final String REVOKE_TOKEN = "post-auth-v1-revoke";
  public static final String CREATE_USER_PROFILE = "post-auth-v1-user-profile";
  public static final String GET_USER_PROFILE = "get-auth-v1-user-profile";
  public static final String UPDATE_USER_PROFILE = "put-auth-v1-user-profile";
  public static final String GET_ORGANIZATIONS = "get-auth-v1-organizations";
  public static final String CREATE_ORGANIZATIONS = "post-auth-v1-admin-organizations";
  public static final String GET_PVDR_REGISTRATION = "get-auth-v1-admin-provider-registrations";
  public static final String UPDATE_PVDR_REGISTRATION = "put-auth-v1-admin-provider-registrations";
  public static final String GET_POLICIES = "get-auth-v1-policies";
  public static final String CREATE_POLICIES = "post-auth-v1-policies";
  public static final String DELETE_POLICIES = "delete-auth-v1-policies";

  /* Query Params */
  public static final String QUERY_FILTER = "filter";

  /* Response Messages */
  public static final String URN_SUCCESS = "urn:dx:as:Success";
  public static final String URN_MISSING_INFO = "urn:dx:as:MissingInformation";
  public static final String URN_INVALID_INPUT = "urn:dx:as:InvalidInput";
  public static final String URN_ALREADY_EXISTS = "urn:dx:as:AlreadyExists";
  public static final String URN_INVALID_ROLE = "urn:dx:as:InvalidRole";
  public static final String URN_INVALID_AUTH_TOKEN = "urn:dx:as:InvalidAuthenticationToken";
  public static final String URN_MISSING_AUTH_TOKEN = "urn:dx:as:MissingAuthenticationToken";

  public static final String TOKEN_FAILED = "Token authentication failed";
  public static final String MISSING_TOKEN = "Missing accessToken";
  public static final String INTERNAL_SVR_ERR = "Internal server error";
  public static final String MISSING_TOKEN_CLIENT = "Missing auth details";
  public static final String INVALID_JSON = "Invalid Json";
  public static final String ERR_TITLE_BAD_REQUEST =
      "Malformed request/missing or malformed request parameters";
  public static final String ERR_DETAIL_BAD_FILTER = "Invalid 'filter' value";
  public static final String INVALID_CLIENT = "Invalid clientId";
  public static final String LOG_FAILED_DISCOVERY = "Fail: Unable to discover keycloak instance; ";

  /* General */
  public static final String NAME = "name";
  public static final String SUB = "sub";
  public static final String ID = "id";
  public static final String ROLES = "roles";
  public static final String USER = "user";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";
  public static final String KID = "kid";
  public static final String SITE = "site";
  public static final String JWT_LEEWAY = "jwtLeeway";
  public static final String STATUS = "status";
  public static final String SSL = "ssl";

  /* Compose failure due to invalid token */
  public static final String INVALID_TOKEN_FAILED_COMPOSE = "INVALID_TOKEN";

  /* SQL Queries */
  public static final String DB_SCHEMA = "test";
  public static final String SQL_GET_USER_ROLES =
      "SELECT u.id, uc.client_id, array_agg(r.role) as roles \n" + "FROM (select id from "
          + DB_SCHEMA + ".users where keycloak_id = $1) u \n" + "LEFT JOIN " + DB_SCHEMA
          + ".roles r ON u.id = r.user_id \n" + "LEFT JOIN " + DB_SCHEMA
          + ".user_clients uc ON u.id = uc.user_id \n"
          + "where r.status='APPROVED' GROUP BY u.id, uc.client_id";

  public static final String SQL_GET_KID_ROLES =
      "SELECT u.id, q.keycloak_id as kid, client_secret, array_agg(r.role) as roles\n"
          + "FROM (select user_id as id, client_secret from " + DB_SCHEMA
          + ".user_clients where client_id = $1) u\n" + "LEFT JOIN " + DB_SCHEMA
          + ".roles r ON u.id = r.user_id\n" + "LEFT JOIN " + DB_SCHEMA
          + ".users q ON u.id = q.id\n"
          + "where r.status='APPROVED' GROUP BY u.id, client_secret, keycloak_id";

}
