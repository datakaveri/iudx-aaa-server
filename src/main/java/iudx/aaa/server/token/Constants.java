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
  
  /* SQL Queries */
  public static final String DB_SCHEMA = "test";
  public static final String GET_USER =
      "SELECT user_id, client_secret FROM " + DB_SCHEMA + ".user_clients WHERE client_id = $1";

  public static final String GET_CLIENT =
      "SELECT client_id FROM " + DB_SCHEMA + ".user_clients WHERE user_id = $1";
  
  public static final String GET_URL =
      "SELECT EXISTS (SELECT 1 FROM " + DB_SCHEMA + ".resource_server WHERE url = $1)";

}
