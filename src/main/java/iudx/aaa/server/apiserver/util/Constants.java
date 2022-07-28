package iudx.aaa.server.apiserver.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static iudx.aaa.server.apiserver.util.Urn.*;

public class Constants {
  // Header params
  public static final String HEADER_AUTHORIZATION = "Authorization";
  public static final String HEADER_HOST = "Host";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  public static final String HEADER_ORIGIN = "Origin";
  public static final String HEADER_REFERER = "Referer";
  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String HEADER_OPTIONS = "options";
  public static final String BEARER = "Bearer";
  public static final String X_CONTENT_TYPE_OPTIONS_NOSNIFF = "nosniff";

  /* Implementation specific headers */
  public static final String HEADER_PROVIDER_ID = "providerId";
  public static final String HEADER_EMAIL = "email";
  public static final String HEADER_ROLE = "role";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";

  /* Configuration & related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_SCHEMA = "databaseSchema";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String POOLSIZE = "poolSize";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTPRE_PASSWORD = "keystorePassword";
  public static final String AUTHSERVER_DOMAIN = "authServerDomain";
  public static final String KEYCLOACK_OPTIONS = "keycloakOptions";
  public static final int PG_CONNECTION_TIMEOUT = 10000;
  public static final int DB_RECONNECT_ATTEMPTS = 5;
  public static final long DB_RECONNECT_INTERVAL_MS = 10000;
  public static final String SERVER_TIMEOUT_MS = "serverTimeoutMs";
  public static final String CORS_REGEX = "corsRegexString";

  // API Documentation endpoint
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String ROUTE_DOC = "/apis";

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
  public static final String GET_POLICIES_REQUEST = "get-auth-v1-policies-requests";
  public static final String POST_POLICIES_REQUEST = "post-auth-v1-policies-requests";
  public static final String PUT_POLICIES_REQUEST = "put-auth-v1-policies-requests";
  public static final String GET_DELEGATIONS = "get-auth-v1-policies-delegations";
  public static final String DELETE_DELEGATIONS = "delete-auth-v1-policies-delegations";
  public static final String CREATE_DELEGATIONS = "post-auth-v1-policies-delegations";
  public static final String CREATE_APD = "post-auth-v1-apd";
  public static final String LIST_APD = "get-auth-v1-apd";
  public static final String UPDATE_APD = "put-auth-v1-apd";
  public static final String GET_CERT = "get-auth-v1-cert";
  
  public static final String TOKEN_ROUTE = "/auth/v1/token";

  /* Query Params */
  public static final String QUERY_FILTER = "filter";

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
  public static final String ERR_TIMEOUT = "Service unavailable";
  public static final String ERR_TITLE_NO_SUCH_API = "No such API/method";
  public static final String ERR_DETAIL_NO_SUCH_API =
      "Refer to the " + ROUTE_DOC + " endpoint for documentation";
  public static final String KS_PARSE_ERROR = "Unable to parse KeyStore";
  public static final String ERR_PROVDERID = "General request- Delegate";
  public static final String INVALID_PROVERID = "Invalid providerId";
  public static final String ERR_DELEGATE = "Invalid delegate request";
  public static final String ERR_DETAIL_SEARCH_USR = "Require both 'email' and 'role' header for search user";
  public static final String ERR_TITLE_SEARCH_USR = "Invalid search user request";
  public static final String SUCC_AUDIT_UPDATE = "Info: Audit log successfully processed";
  public static final String ERR_AUDIT_UPDATE = "Fail: Error in processing audit log";

  /* Static JSON responses */
  public static final String JSON_TIMEOUT = "{\"type\":\"" + URN_MISSING_INFO + "\", \"title\":\""
      + ERR_TIMEOUT + "\", \"detail\":\"" + ERR_TIMEOUT + "\"}";

  public static final String JSON_NOT_FOUND =
      "{\"type\":\"" + URN_INVALID_INPUT + "\", \"title\":\"" + ERR_TITLE_NO_SUCH_API
          + "\", \"detail\":\"" + ERR_DETAIL_NO_SUCH_API + "\"}";

  /* General */
  public static final String NAME = "name";
  public static final String SUB = "sub";
  public static final String ID = "id";
  public static final String ROLES = "roles";
  public static final String USER = "user";
  public static final String KEYCLOAK_AAA_SERVER_CLIENT_ID = "keycloakAaaClientId";
  public static final String KEYCLOAK_AAA_SERVER_CLIENT_SECRET = "keycloakAaaClientSecret";
  public static final String KID = "kid";
  public static final String KEYCLOAK_SITE = "keycloakSite";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KEYCLOAK_JWT_LEEWAY = "keycloakJwtLeeway";
  public static final String STATUS = "status";
  public static final String SSL = "ssl";
  public static final String KS_ALIAS = "ES256";
  public static final String PUB_KEY = "publicKey";
  public static final String CERTIFICATE = "cert";
  public static final String REQUEST = "request";
  public static final String DATA = "data";
  public static final String CONTEXT_SEARCH_USER = "searchUserData";
  public static final String BODY = "body";
  public static final String API = "api";
  public static final String METHOD = "method";
  public static final String USER_ID = "userId";

  /* Compose failure due to invalid token */
  public static final String INVALID_TOKEN_FAILED_COMPOSE = "INVALID_TOKEN";
  
  /* HashSet to Match success status */
  public static final Set<Integer> successStatus =
      new HashSet<Integer>(Arrays.asList(200, 201, 202));


  /* SQL Queries */
  public static final String SQL_GET_USER_ROLES = 
      "select roles.user_id AS id, coalesce(array_agg(roles.role) filter (where status = 'APPROVED'), '{}') AS roles"
      + ", client_id FROM roles JOIN user_clients"
      + " ON roles.user_id = user_clients.user_id"
      + " WHERE roles.user_id = (SELECT id FROM users WHERE keycloak_id = $1)"
      + " GROUP BY client_id, roles.user_id";

  public static final String SQL_GET_KID_ROLES =
      "SELECT u.id, q.keycloak_id as kid, client_secret, array_agg(r.role) as roles\n"
          + "FROM (select user_id as id, client_secret from "
          + "user_clients where client_id = $1) u\n" + "LEFT JOIN "
          + "roles r ON u.id = r.user_id\n" + "LEFT JOIN " 
          + "users q ON u.id = q.id\n"
          + "where r.status='APPROVED' GROUP BY u.id, client_secret, keycloak_id";
  
  public static final String CHECK_DELEGATE =
      "SELECT * FROM policies pol "
      + "INNER JOIN delegations del ON "
      + "pol.owner_id = del.owner_id AND pol.user_id = del.user_id "
      + "WHERE del.user_id = $1 AND "
      + "del.owner_id = $2 AND "
      + "del.resource_server_id = pol.item_id AND "
      + "pol.status = 'ACTIVE' AND "
      + "pol.expiry_time > now()";
  
  public static final String GET_DELEGATE = 
      "WITH auth AS (\n" + 
      "    SELECT owner_id, id FROM resource_server_view WHERE url = $1::text\n" +
      "), delegate AS (\n" + 
      "    SELECT resource_server_id, user_id,owner_id FROM delegations \n" + 
      "    WHERE user_id = $2::uuid \n" + 
      "    AND owner_id = $3::uuid \n" + 
      "    AND status = 'ACTIVE' \n" + 
      "    AND resource_server_id = (SELECT id FROM auth)\n" + 
      "), policy AS (\n" + 
      "    SELECT * FROM policies WHERE item_id = (SELECT id FROM auth) \n" + 
      "    AND owner_id = (SELECT owner_id FROM auth) \n" + 
      "    AND user_id = (SELECT user_id FROM delegate)\n" + 
      "    AND status = 'ACTIVE' \n" + 
      "    AND expiry_time > now()\n" + 
      ") SELECT * FROM policy";

}
