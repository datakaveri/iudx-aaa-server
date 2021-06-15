package iudx.aaa.server.token;

public class Constants {
  
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  
  public static final int BCRYPT_SALT_LEN = 16;
  public static final int BCRYPT_LOG_COST = 12;
  public static final String JWT_ALGORITHM = "ES256";
  public static String CLAIM_ISSUER = "";
  public static final long CLAIM_EXPIRY = 600; //In Seconds 
  
  public static final int PG_CONNECTION_TIMEOUT = 10000;
  
  
  /* SQL Queries */
  public static final String GET_CLIENT =
      "SELECT user_id, client_secret FROM user_clients WHERE client_id = $1";

}
