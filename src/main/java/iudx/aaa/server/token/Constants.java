package iudx.aaa.server.token;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class Constants {
  
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  
  public static final int BCRYPT_SALT_LEN = 16;
  public static final int BCRYPT_LOG_COST = 12;
  public static final String JWT_ALGORITHM = "ES256";
  public static String CLAIM_ISSUER = "";
  public static final long CLAIM_EXPIRY = 60 * 60 * 12; //In Seconds 
  
  public static final String RS_REVOKE_URI = "/admin/revokeToken";
  public static final String RS_REVOKE_BODY_SUB = "sub";
  public static final int DEFAULT_HTTPS_PORT = 443;
  
  /* Configuration & related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_SCHEMA = "databaseSchema";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String POOLSIZE = "poolSize";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTPRE_PASSWORD = "keystorePassword";
  public static final String AUTHSERVER_DOMAIN = "authServerDomain";

  public static final String KEYCLOAK_HOST = "keycloakHost";
  public static final String KEYCLOAK_PORT = "keycloakPort";
  public static final String KEYCLOAK_TOKEN_URI = "keycloakTokenUri";
  public static final String KEYCLOAK_AAA_SERVER_CLIENT_ID = "keycloakAaaClientId";
  public static final String KEYCLOAK_AAA_SERVER_CLIENT_SECRET = "keycloakAaaClientSecret";
  public static final int PG_CONNECTION_TIMEOUT = 10000;
  public static final int DB_RECONNECT_ATTEMPTS = 5;
  public static final long DB_RECONNECT_INTERVAL_MS = 10000;
  
  /* General */ 
  public static final String STATUS = "status";
  public static final String ALLOW = "allow";
  public static final String DENY = "deny";
  public static final String SUCCESS = "success";
  public static final String APD_INTERACTION = "apd-interaction";
  public static final String FAILED = "failed";
  public static final String DESC = "desc";
  public static final String ACCESS_TOKEN = "accessToken";
  public static final String APD_TOKEN = "apdToken";
  public static final String RS_URL = "rsUrl";
  public static final String HOST = "host";
  public static final String PORT = "port";
  public static final String URI = "uri";
  public static final String BODY = "BODY";
  public static final String ROLE_LIST = "roleList";
  public static final String EXISTS = "exists";
  public static final String TYPE = "type";
  public static final String OWNER = "owner";
  public static String keystorePath = null;
  public static String keystorePassword = null;
  
  public static final String ITEM_ID = "itemId";
  public static final String ITEM_TYPE = "itemType";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";
  public static final String AUDIENCE = "audience";
  public static final String URL = "url";
  public static final String USER_ID = "userId";
  public static final String SESSION_ID = "sessionId";
  public static final String CONSTRAINTS = "constraints";
  public static final String RESOURCE_SVR = "resource_server";
    
  /* JWT Constants & related */
  public static final String TOKEN = "token";
  public static final String SUB = "sub";
  public static final String ISS = "iss";
  public static final String AUD = "aud";
  public static final String EXP = "exp";
  public static final String NFB = "nbf";
  public static final String IAT = "iat";
  public static final String JTI = "jti";
  public static final String ROLE = "role";
  public static final String ITYPE = "ityp";
  public static final String IID = "iid";
  public static final String CONS = "cons";
  public static final String APD = "apd";
  public static final String INTROSPECT_USERINFO = "userInfo";
  
  /* JWT Constants for APD token */
  public static final String LINK = "link";
  public static final String SID = "sid";

  
  public static final String GRANT_TYPE = "grant_type";
  public static final String CLIENT_CREDENTIALS = "client_credentials";
  
  /* Bidrectional itemType map */
  public static BiMap<String, String> ITEM_TYPE_MAP = HashBiMap.create();
  static {
    ITEM_TYPE_MAP.put("ri", "resource");
    ITEM_TYPE_MAP.put("rg","resource_group");
    ITEM_TYPE_MAP.put("rs","resource_server");
  }
  
  /* Response Messages */
  public static final String REQ_RECEIVED = "Info: Request received";
  public static final String LOG_DB_ERROR = "Fail: Databse query; ";
  public static final String LOG_UNAUTHORIZED = "Fail: Unauthorized access; ";
  public static final String LOG_TOKEN_AUTH = "Fail: Authentication failed; ";
  public static final String LOG_USER_SECRET = "Fail: Invalid clientSecret format; ";
  public static final String LOG_TOKEN_SUCC = "Info: Policy evaluation succeeded; JWT generated & signed";
  public static final String LOG_REVOKE_REQ = "Info: Revoke request succeeded";
  public static final String LOG_PARSE_TOKEN = "Fail: Unable to parse accessToken";
  
  public static final String INTERNAL_SVR_ERR = "Internal server error";
  public static final String INVALID_CLIENT_ID_SEC = "Invalid clientId/clientSecret";
  public static final String INVALID_ROLE = "Role not defined";
  public static final String CANNOT_REVOKE_ON_AUTH = "Cannot revoke tokens on auth server";
  public static final String INVALID_POLICY = "Policy evaluation failed";
  public static final String TOKEN_SUCCESS = "Token created";
  public static final String ERR_TITLE_APD_INTERACT_REQUIRED = "APD interaction required";
  public static final String ERR_DETAIL_APD_INTERACT_REQUIRED =
      "The APD requires extra information to grant access."
      + " Please use the apdToken and visit the link to interact with the APD";
  public static final String TOKEN_REVOKED = "Token revoked";
  public static final String TOKEN_AUTHENTICATED = "Token authenticated";
  public static final String INVALID_USERID = "Empty/null userId";
  public static final String INVALID_RS_URL = "Incorrect resourceServer URL";
  public static final String INVALID_USER_CLIENT = "Incorrect userId/clientId";
  public static final String INVALID_CLIENT = "Invalid clientId";
  public static final String FAILED_REVOKE = "Revoke request failed";
  public static final String MISSING_TOKEN = "Missing accessToken";
  public static final String TOKEN_FAILED = "Token authentication failed";
  public static final String POLICY_SUCCESS =  "Policy evaluation succeeded";
  public static final String INVALID_SUB = "Invalid subject ID";
  public static final String ERR_ADMIN = "Invalid role for requested resource";
  public static final String ADMIN = "admin";
  public static final String CONSUMER = "consumer";
  public static final String PROVIDER = "provider";
  public static final String TRUSTEE = "trustee";
  
  /* SQL Queries */
  public static final String CHECK_USER =
      "SELECT EXISTS (SELECT 1 FROM user_clients WHERE user_id = $1)";

  public static final String GET_CLIENT =
      "SELECT client_id FROM user_clients WHERE user_id = $1";
  
  public static final String GET_URL =
      "SELECT EXISTS (SELECT 1 FROM resource_server WHERE url = $1)";
  
  public static final String GET_RS = "SELECT resource_server.id FROM resource_server LEFT JOIN resource_server_admins" +
          " ON resource_server.id = resource_server_admins.resource_server_id" +
          " WHERE url = $1::text" +
          " AND (resource_server.owner_id = $2::uuid OR resource_server_admins.admin_id = $2::uuid)" +
          " LIMIT 1";

  public static final String CHECK_APD_EXISTS_BY_URL = "SELECT id FROM apds WHERE url = $1";

  public static final String CHECK_RS_EXISTS_BY_URL = "SELECT id FROM resource_server WHERE url = $1";

  public static final String CHECK_APD_OWNER =
      "SELECT owner_id AS owner FROM apds WHERE url = $1 AND owner_id = $2::uuid";
}
