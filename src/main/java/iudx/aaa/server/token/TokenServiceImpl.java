package iudx.aaa.server.token;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
import static iudx.aaa.server.token.Constants.*;
import java.util.UUID;

/**
 * The Token Service Implementation.
 * <h1>Token Service Implementation</h1>
 * <p>
 * The Token Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.token.TokenService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class TokenServiceImpl implements TokenService {

  private static final Logger LOGGER = LogManager.getLogger(TokenServiceImpl.class);

  private PostgresClient pgClient;
  private JWTAuth provider;
  private PolicyService policyService;

  public TokenServiceImpl(PostgresClient pgClient, PolicyService policyService, JWTAuth provider) {
    this.pgClient = pgClient;
    this.policyService = policyService;
    this.provider = provider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService createToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    
    String clientId = request.getString("clientId");
    String clientSecret = request.getString("clientSecret");
    String hashedClientSecret = BCrypt.hashpw(clientSecret, BCrypt.gensalt(12));
    
    String query = CLIENT_VALIDATION.replace("$1", clientId)
                                    .replace("$2", hashedClientSecret);
    
    pgClient.executeAsync(query).onComplete(dbHandler ->{
      if(dbHandler.succeeded()) {
        if(dbHandler.result().size() == 1) {
          RowSet<Row> result = dbHandler.result();
          for(Row each: result) {
            request.put("userId", each.toJson().getString("user_id"));
          }
          //TODO: handler for verifying the policy and its response.
          policyService.listPolicy(request, policyHandler -> {
            if (policyHandler.succeeded()) {
              request.put("constraints", policyHandler.result().getJsonObject("constraints"));
              
              
              String jwt = getJwt(request);
              handler.handle(Future
                  .succeededFuture(new JsonObject().put("status", "success").put("jwt", jwt)));
              
            } else if (policyHandler.failed()) {
              LOGGER.error("Fail: Unauthorized access; Failed policy permission");
              handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                  .put("desc", "Unauthorized user").toString()));
            }
          });
        } else {
          LOGGER.error("Fail: Invalid clientId/clientSecret");
          handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
              .put("desc", "Invalid clientId/clientSecret").toString()));
        }
      } else {
        LOGGER.error("Fail: Databse query; " + dbHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
            .put("desc", dbHandler.cause().getLocalizedMessage()).toString()));
      }
    });
    
    return this;
  }

  @Override
  public TokenService revokeToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

  @Override
  public TokenService listToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }
  
  /**
   * Generates the JWT token using the request data.
   * 
   * @param request
   * @return jwtToken
   */
  private String getJwt(JsonObject request) {
    String uuid = UUID.randomUUID().toString();
    long timestamp = System.currentTimeMillis() / 1000;
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM);
    
    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    claims.put("sub", request.getString("clientId"))
          .put("iss", request.getString("issuer"))
          .put("aud", request.getValue("audience"))
          .put("exp", timestamp + request.getString("expiry"))
          .put("nbf", timestamp)
          .put("iat", timestamp)
          .put("jti", uuid)
          .put("item_id", request.getString("itemId"))
          .put("item_type", request.getString("itemType"))
          .put("role", request.getString("role"))
          .put("constraints", request.getJsonObject("constraints"));
    
    String token = provider.generateToken(claims, options);
    return token;
  }

}
