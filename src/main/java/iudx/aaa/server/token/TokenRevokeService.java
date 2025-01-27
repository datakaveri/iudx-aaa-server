package iudx.aaa.server.token;

import static iudx.aaa.server.token.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenRevokeService {
  private static final Logger LOGGER = LogManager.getLogger(TokenRevokeService.class);
  private WebClient client;

  /**
   * Constructor initializing WebClient.
   *
   * @param vertx which is a Vert.x instance
   */
  public TokenRevokeService(Vertx vertx) {

    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(true).setTrustAll(false);

    this.client = WebClient.create(vertx, clientOptions);
  }

  /**
   * Handles token revocation
   *
   * @param request is a JSON object containing the user ID and URL of server to revoke at
   * @param adminToken is the admin token to be presented at the server
   * @return Future of type JsonObject
   */
  Future<JsonObject> httpRevokeRequest(JsonObject request, String adminToken) {

    LOGGER.info("Info : Processing token revocation");
    Promise<JsonObject> promiseHandler = Promise.promise();

    JsonObject rsPayload = new JsonObject();
    rsPayload.put(RS_REVOKE_BODY_SUB, request.getString(USER_ID));

    request.put(BODY, rsPayload).put(URI, RS_REVOKE_URI);

    httpPostAsync(request, adminToken)
        .onSuccess(
            reqHandler -> {
              promiseHandler.complete(new JsonObject());
            })
        .onFailure(
            reqHandler -> {
              promiseHandler.fail(reqHandler.getMessage());
            });

    return promiseHandler.future();
  }

  /**
   * Future to handles http post request to External services.
   *
   * @param requestBody which is a JsonObject containing the URL, payload body and URI
   * @param token the admin token to be presented to the server in the header
   * @return promise and future associated with the promise.
   */
  private Future<Void> httpPostAsync(JsonObject requestBody, String token) {

    Promise<Void> promise = Promise.promise();
    RequestOptions options = new RequestOptions();
    options.setHost(requestBody.getString(RS_URL));
    options.setPort(requestBody.getInteger(PORT, DEFAULT_HTTPS_PORT));
    options.setURI(requestBody.getString(URI));

    JsonObject body = requestBody.getJsonObject(BODY);

    client
        .request(HttpMethod.POST, options)
        .putHeader(TOKEN, token)
        .expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON)
        .sendJsonObject(body)
        .onSuccess(
            reqHandler -> {
              String type =
                  Optional.ofNullable((String) reqHandler.bodyAsJsonObject().getString(TYPE))
                      .orElse("");

              if (type.isBlank() || !type.toLowerCase().endsWith(SUCCESS)) {
                promise.fail("Invalid type URN/type not in response");
                return;
              }

              LOGGER.debug("Info: ResourceServer request completed");
              promise.complete();
            })
        .onFailure(
            reqHandler -> {
              LOGGER.debug("Error: ResourceServer request failed; {}", reqHandler.getMessage());
              promise.fail(reqHandler.getMessage());
            });

    return promise.future();
  }
}
