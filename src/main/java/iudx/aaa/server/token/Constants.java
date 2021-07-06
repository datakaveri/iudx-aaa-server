package iudx.aaa.server.token;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class Constants {
  
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  
  public static final int BCRYPT_SALT_LEN = 16;
  public static final int BCRYPT_LOG_COST = 12;
  public static final String JWT_ALGORITHM = "ES256";
  public static String CLAIM_ISSUER = "";
  public static final long CLAIM_EXPIRY = 60 * 60 * 12; //In Seconds 
  
  public static final String RS_REVOKE_URN = "/token/revocation";
  public static final int DEFAULT_HTTPS_PORT = 443;
  
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
  
  /* General */ 
  public static final String STATUS = "status";
  public static final String ALLOW = "allow";
  public static final String DENY = "deny";
  public static final String SUCCESS = "success";
  public static final String FAILED = "failed";
  public static final String DESC = "desc";
  public static final String ACCESS_TOKEN = "accessToken";
  public static final String RS_URL = "rsUrl";
  public static final String PORT = "port";
  public static final String URI = "uri";
  public static final String BODY = "BODY";
  public static final String ROLE_LIST = "roleList";
  public static final String EXISTS = "exists";
  public static final String TYPE = "type";
  
  public static final String ITEM_ID = "itemId";
  public static final String ITEM_TYPE = "itemType";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_SECRET = "clientSecret";
  public static final String AUDIENCE = "audience";
  public static final String USER_ID = "userId";
  public static final String CONSTRAINTS = "constraints";
    
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
  
  public static final String GRANT_TYPE = "grant_type";
  public static final String CLIENT_CREDENTIALS = "client_credentials";
  
  /* Bidrectional itemType map */
  public static BiMap<String, String> ITEM_TYPE_MAP = HashBiMap.create();
  static {
    ITEM_TYPE_MAP.put("ri", "resource");
    ITEM_TYPE_MAP.put("rg","resourceGroup");
  }
  
  /* Response Messages */
  public static final String URN_SUCCESS = "urn:dx:as:Success";
  public static final String URN_MISSING_INFO = "urn:dx:as:MissingInformation";
  public static final String URN_INVALID_INPUT = "urn:dx:as:InvalidInput";
  public static final String URN_ALREADY_EXISTS = "urn:dx:as:AlreadyExists";
  public static final String URN_INVALID_ROLE = "urn:dx:as:InvalidRole";
  public static final String URN_INVALID_AUTH_TOKEN = "urn:dx:as:InvalidAuthenticationToken";
  
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
  public static final String INVALID_POLICY = "Policy evaluation failed";
  public static final String TOKEN_SUCCESS = "Token created";
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
  
  
  /* SQL Queries */
  public static final String DB_SCHEMA = "test";
  public static final String GET_USER =
      "SELECT user_id, client_secret FROM " + DB_SCHEMA + ".user_clients WHERE client_id = $1";

  public static final String GET_CLIENT =
      "SELECT client_id FROM " + DB_SCHEMA + ".user_clients WHERE user_id = $1";
  
  public static final String GET_URL =
      "SELECT EXISTS (SELECT 1 FROM " + DB_SCHEMA + ".resource_server WHERE url = $1)";

}
