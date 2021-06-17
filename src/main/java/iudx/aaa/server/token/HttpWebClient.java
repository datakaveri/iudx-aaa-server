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

public class HttpWebClient {
  private static final Logger LOGGER = LogManager.getLogger(HttpWebClient.class);
  private WebClient client;
  private JsonObject httpOptions;

  public HttpWebClient(Vertx vertx, JsonObject flinkOptions) {
    
    WebClientOptions clientOptions = new WebClientOptions()
        .setSsl(true)
        .setVerifyHost(false)
        .setTrustAll(true);
    
    this.client = WebClient.create(vertx,clientOptions);
    this.httpOptions = flinkOptions;
  }
  
  
  /**
   * Handles token revocation requests. Generates token using KeyCloack 
   * and send further request to resourceServer.
   * 
   * @param request which is a JsonObject
   * @param handler which is a AsyncResult Handler
   * @return future event which upon completion
   */
  HttpWebClient httpRevokeRequest(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    
    LOGGER.info("Info: "+ LOGGER.getName() + ": procssing token revocation");
    httpPostFormAsync(httpOptions).compose(kcHandler -> {
      request.put("token", kcHandler.getString("access_token"));
      return httpPostAsync(request);
    }).onComplete(reqHandler -> {
      if (reqHandler.succeeded()) {
        LOGGER.debug("Info: Token revocation request succedded");
        handler.handle(Future.succeededFuture(reqHandler.result()));
      } else if (reqHandler.failed()) {
        LOGGER.error("Fail: Token revocation request failed; " + reqHandler.cause().getMessage());
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
    options.setHost(requestBody.getString("rsUrl"));
    options.setPort(requestBody.getInteger("port",DEFAULT_HTTPS_PORT));
    options.setURI(requestBody.getString("url"));
    
    String token = requestBody.getString("token");
    JsonObject body = requestBody.getJsonObject("body");
    
    client.request(HttpMethod.POST, options).putHeader("token", token).sendJsonObject(body, reqHandler -> {
      if (reqHandler.succeeded()) {
        if (reqHandler.result().statusCode() == 200 || reqHandler.result().statusCode() == 202) {
          LOGGER.debug("Info: Flink request completed");
          promise.complete(reqHandler.result().bodyAsJsonObject());
          return;
        } else {
          LOGGER.error("Error: Flink request failed; " + reqHandler.result().bodyAsString());
          promise.fail(reqHandler.result().bodyAsString());
          return;
        }
      } else if (reqHandler.failed()) {
        LOGGER.debug("Error: Flink request failed; " + reqHandler.cause().getMessage());
        promise.fail(reqHandler.cause());
        return;
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
    options.setPort(8443);
    
    MultiMap bodyForm = MultiMap.caseInsensitiveMultiMap();
    bodyForm.set("grant_type", "client_credentials")
            .set("client_id", requestBody.getString("clientId"))
            .set("client_secret", requestBody.getString("clientSecret"));
    
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
