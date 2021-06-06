package iudx.aaa.server.token;

public class Constants {
  
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  
  public static final String JWT_ALGORITHM = "ES256";
  public static final String CLAIM_ISSUER = "";
  public static final int CLAIM_EXPIRY = 1000; //In Seconds 
  
  
  /* SQL Queries */
  public static final String CLIENT_VALIDATION = "SELECT user_id from user_client "
      + "where client_id = '$1' and client_secret = '$2'";

}
