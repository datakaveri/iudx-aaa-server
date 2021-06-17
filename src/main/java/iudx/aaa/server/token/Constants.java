package iudx.aaa.server.token;

public class Constants {
  
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  
  public static final int BCRYPT_SALT_LEN = 16;
  public static final int BCRYPT_LOG_COST = 12;
  public static final String JWT_ALGORITHM = "ES256";
  public static String CLAIM_ISSUER = "";
  public static final long CLAIM_EXPIRY = 60 * 60 * 12; //In Seconds 
  public static final int PG_CONNECTION_TIMEOUT = 10000;
  
  public static final String RS_REVOKE_URN = "/token/revocation";
  public static final int DEFAULT_HTTPS_PORT = 443;
  
  
  /* SQL Queries */
  public static final String DB_SCHEMA = "test";
  public static final String GET_USER =
      "SELECT user_id, client_secret FROM " + DB_SCHEMA + ".user_clients WHERE client_id = $1";

  public static final String GET_CLIENT =
      "SELECT client_id FROM " + DB_SCHEMA + ".user_clients WHERE user_id = $1";
  
  public static final String GET_URL =
      "SELECT EXISTS (SELECT 1 FROM " + DB_SCHEMA + ".resource_server WHERE url = $1)";

}
