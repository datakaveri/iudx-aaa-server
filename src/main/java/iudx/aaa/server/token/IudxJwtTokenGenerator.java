package iudx.aaa.server.token;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.token.Constants.ACCESS_TOKEN;
import static iudx.aaa.server.token.Constants.APD;
import static iudx.aaa.server.token.Constants.APD_TOKEN;
import static iudx.aaa.server.token.Constants.AUD;
import static iudx.aaa.server.token.Constants.CLAIM_EXPIRY;
import static iudx.aaa.server.token.Constants.CLAIM_ISSUER;
import static iudx.aaa.server.token.Constants.CONS;
import static iudx.aaa.server.token.Constants.CONSTRAINTS;
import static iudx.aaa.server.token.Constants.EXP;
import static iudx.aaa.server.token.Constants.IAT;
import static iudx.aaa.server.token.Constants.IID;
import static iudx.aaa.server.token.Constants.ISS;
import static iudx.aaa.server.token.Constants.ITEM_ID;
import static iudx.aaa.server.token.Constants.ITEM_TYPE;
import static iudx.aaa.server.token.Constants.ITEM_TYPE_MAP;
import static iudx.aaa.server.token.Constants.JWT_ALGORITHM;
import static iudx.aaa.server.token.Constants.LINK;
import static iudx.aaa.server.token.Constants.ROLE;
import static iudx.aaa.server.token.Constants.SESSION_ID;
import static iudx.aaa.server.token.Constants.SID;
import static iudx.aaa.server.token.Constants.SUB;
import static iudx.aaa.server.token.Constants.URL;
import static iudx.aaa.server.token.Constants.USER_ID;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;


/**
 * A Jwt utility class to create different Jwt toke, based on requests.
 * 
 *
 */
public class IudxJwtTokenGenerator {
  
  private JWTAuth provider;
  
  public IudxJwtTokenGenerator( JWTAuth provider) {
    this.provider=provider;
  }
  
  /**
   * Generates the JWT token using the request data.
   * 
   * @param request
   * @return jwtToken
   */
  public JsonObject getJwt(JsonObject request) {
    
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM);
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    String itemType = request.getString(ITEM_TYPE);
    String iid = ITEM_TYPE_MAP.inverse().get(itemType)+":"+request.getString(ITEM_ID);
    String audience = request.getString(URL);
    
    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    //add apd cons
    claims.put(SUB, request.getString(USER_ID))
          .put(ISS, CLAIM_ISSUER)
          .put(AUD, audience)
          .put(EXP, expiry)
          .put(IAT, timestamp)
          .put(IID, iid)
          .put(ROLE, request.getString(ROLE))
          .put(CONS, request.getJsonObject(CONSTRAINTS, new JsonObject()));

    if(request.containsKey(APD_CONSTRAINTS))
      claims.put(APD,request.getJsonObject(APD_CONSTRAINTS, new JsonObject()));

    String token = provider.generateToken(claims, options);

    JsonObject tokenResp = new JsonObject();
    tokenResp.put(ACCESS_TOKEN, token).put("expiry", expiry).put("server",
        audience);
    return tokenResp;
  }

  /**
   * Generates the JWT token used for APD interaction using the request data.
   * 
   * @param request a JSON object containing
   *        <ul>
   *        <li><em>url</em> : The URL of the APD to be called. This is placed in the <em>aud</em>
   *        field</li>
   *        <li><em>userId</em> : The user ID of the user requesting access</li>
   *        <li><em>sessionId</em> : The sessionId sent by the APD</li>
   *        <li><em>link</em> : The link to visit sent by the APD</li>
   *        </ul>
   * @return jwtToken a JSON object containing the <i>accessToken</i>, expiry and server (audience)
   */
  public JsonObject getApdJwt(JsonObject request) {
    
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM);
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    String sessionId = request.getString(SESSION_ID);
    String link = request.getString(LINK);
    String audience = request.getString(URL);
    
    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    claims.put(SUB, request.getString(USER_ID))
          .put(ISS, CLAIM_ISSUER)
          .put(AUD, audience)
          .put(EXP, expiry)
          .put(IAT, timestamp)
          .put(SID, sessionId)
          .put(LINK, link);
    
    String token = provider.generateToken(claims, options);

    JsonObject tokenResp = new JsonObject();
    tokenResp.put(APD_TOKEN, token).put("expiry", expiry).put("server",
        audience).put(LINK, link);
    return tokenResp;
  }

}
