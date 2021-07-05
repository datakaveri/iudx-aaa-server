package iudx.aaa.server.token;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import static iudx.aaa.server.token.Constants.*;

public class TokenRevokeService {
  private static final Logger LOGGER = LogManager.getLogger(TokenRevokeService.class);
  private WebClient client;
  private JsonObject httpOptions;

  /**
   * Constructor initializing WebClient.
   * 
   * @param vertx which is a Vert.x instance
   * @param keyCloakOptions which is a JsonObject
   */
  public TokenRevokeService(Vertx vertx, JsonObject keyCloakOptions) {
    
    WebClientOptions clientOptions = new WebClientOptions()
        .setSsl(true)
        .setVerifyHost(false)
        .setTrustAll(true);
    
    this.client = WebClient.create(vertx,clientOptions);
    this.httpOptions = keyCloakOptions;
  }
  
  
  /**
   * Handles token revocation requests. Generates token using KeyCloack 
   * and send further request to resourceServer.
   * 
   * @param request which is a JsonObject
   * @param handler which is a AsyncResult Handler
   * @return future event which upon completion
   */
  TokenRevokeService httpRevokeRequest(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.info("Info : Procssing token revocation");

    JsonObject rsPayload = new JsonObject();
    rsPayload.put("user_id", request.getValue(CLIENT_ID))
             .put("current-token-duration",CLAIM_EXPIRY);
    
    request.put(BODY, rsPayload).put(URI, RS_REVOKE_URN);

    httpPostFormAsync(httpOptions).compose(kcHandler -> {
      request.put(TOKEN, kcHandler.getString("access_token"));
      return httpPostAsync(request);
    }).onComplete(reqHandler -> {
      if (reqHandler.succeeded()) {
        handler.handle(Future.succeededFuture(reqHandler.result()));
      } else if (reqHandler.failed()) {
        handler.handle(Future.failedFuture(reqHandler.cause()));
      }
    });

    return this;
  }
  
  /**
   * Future to handles http post request to External services.
   * 
   * @param requestBody which is a JsonObject
   * @return promise and future associated with the promise.
   */
  private Future<JsonObject> httpPostAsync(JsonObject requestBody) {

    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions();
    options.setHost(requestBody.getString(RS_URL));
    options.setPort(requestBody.getInteger(PORT,DEFAULT_HTTPS_PORT));
    options.setURI(requestBody.getString(URI));

    String token = requestBody.getString(TOKEN);
    JsonObject body = requestBody.getJsonObject(BODY);

    client.request(HttpMethod.POST, options).putHeader(TOKEN, token).sendJsonObject(body,
        reqHandler -> {
          if (reqHandler.succeeded()) {
            LOGGER.debug("Info: ResourceServer request completed");
            promise.complete(reqHandler.result().bodyAsJsonObject());
          } else if (reqHandler.failed()) {
            LOGGER.debug("Error: ResourceServer request failed; " + reqHandler.cause().getMessage());
            promise.fail(reqHandler.cause());
          }
        });
    return promise.future();
  }
  
  /**
   * To Generate JWT using KeyCloak credentials.
   * Performs POST Multipart/Form request to KeyCloak.
   * 
   * @param requestBody which is a JsonObject
   * @return promise and future associated with the promise.
   */
  private Future<JsonObject> httpPostFormAsync(JsonObject requestBody) {

    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions(requestBody);
    
    MultiMap bodyForm = MultiMap.caseInsensitiveMultiMap();
    bodyForm.set(GRANT_TYPE, CLIENT_CREDENTIALS)
            .set("client_id", requestBody.getString(CLIENT_ID))
            .set("client_secret", requestBody.getString(CLIENT_SECRET));
    
    client.request(HttpMethod.POST, options).sendForm(bodyForm, reqHandler -> {
      if (reqHandler.succeeded()) {
        LOGGER.debug("Info: Keycloak request completed; JWT generated");
        promise.complete(reqHandler.result().bodyAsJsonObject());
      } else if (reqHandler.failed()) {
        LOGGER.error("Fail: Keycloak request failed; " + reqHandler.cause());
        promise.fail(reqHandler.cause());
      }
    });
    return promise.future();
  }
}
