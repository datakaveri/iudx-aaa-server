package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_READ_USERCLASSES_API;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.APD_URN_REGEX;
import static iudx.aaa.server.apd.Constants.APD_VERIFY_API;
import static iudx.aaa.server.apd.Constants.APD_VERIFY_AUTH_HEADER;
import static iudx.aaa.server.apd.Constants.APD_VERIFY_BEARER;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_RESPOND;
import static iudx.aaa.server.apiserver.util.Urn.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * ApdWebClient is used to perform HTTP calls to the APD. It is a separate class so that it can be
 * easily mocked when testing.
 *
 */
public class ApdWebClient {
  private WebClient webClient;
  private static final Logger LOGGER = LogManager.getLogger(ApdWebClient.class);

  public ApdWebClient(WebClient wc) {
    this.webClient = wc;
  }

  static Response failureResponse = new ResponseBuilder().type(URN_INVALID_INPUT)
      .title(ERR_TITLE_APD_NOT_RESPOND).detail(ERR_DETAIL_APD_NOT_RESPOND).status(400).build();

  /**
   * The function is used to check if the Access Policy Domain exists. This is needed when the APD
   * is being registered. The read user class API is called on the URL provided. A succeeded future
   * with value <i>true</i> is returned if the check is successful. A failed future with a
   * ComposeException is returned if there is a failure when reaching the APD or in case of an
   * internal error.
   * 
   * @param url The URL supplied during APD registration
   * @return a boolean future indicating the result.
   */
  public Future<Boolean> checkApdExists(String url) {
    Promise<Boolean> promise = Promise.promise();
    RequestOptions options = new RequestOptions();
    options.setHost(url).setPort(443).setURI(APD_READ_USERCLASSES_API);

    webClient.request(HttpMethod.GET, options).expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON).send().onSuccess(resp -> {
          Optional<JsonObject> response = Optional.ofNullable(resp.bodyAsJsonObject());
          /*
           * Currently, the only validation we do is to check if the APD returns a JSON object. We
           * can add further validations if required
           */
          if (response.isPresent()) {
            promise.complete(true);
          } else {
            promise.fail(new ComposeException(failureResponse));
          }
        }).onFailure(err -> {
          LOGGER.error(err.getMessage());
          promise.fail(new ComposeException(failureResponse));
        });
    return promise.future();
  }

  /**
   * Call an APD's verify endpoint.
   * 
   * @param url the URL of the APD
   * @param authToken the auth server token to be added as an Authorization header
   * @param request the JSON request body
   * @return a future. A succeeded future with a JSON object is returned if the APD responds as
   *         expected. A failed future with a ComposeException is returned otherwise.
   */
  public Future<JsonObject> callVerifyApdEndpoint(String url, String authToken,
      JsonObject request) {
    Promise<JsonObject> promise = Promise.promise();
    
    RequestOptions options = new RequestOptions();
    options.setHost(url).setPort(443).setURI(APD_VERIFY_API);
    options.addHeader(APD_VERIFY_AUTH_HEADER, APD_VERIFY_BEARER + authToken);

    webClient.request(HttpMethod.POST, options).expect(ResponsePredicate.JSON)
        .sendJsonObject(request).compose(body -> checkApdResponse(body))
        .onSuccess(resp -> {
          promise.complete(resp);
          LOGGER.info("APD {} responded to access request by {}", url,
              request.getJsonObject("user").getString("id"));
        }).onFailure(err -> {
          LOGGER.error(err.getMessage());
          promise.fail(new ComposeException(failureResponse));
        });
    return promise.future();
  }

  /**
   * Check if the response sent back by the APD after querying the verify endpoint has:
   * <ul>
   * <li>HTTP status code 200 or 403</li>
   * <li>Is valid JSON</li>
   * <li>Has <i>type</i> keyword with value either <i>allow URN/deny URN</i></li>
   * <li>If <i>deny URN</i>, then must contain <i>detail</i> also</li>
   * <li>If the appropriate URN+status code combo is sent.</li>
   * </ul>
   * 
   * @param body the buffer response from the web client
   * @return a future with the JSON object body if all checks pass. Else a failed future is
   *         returned.
   */
  Future<JsonObject> checkApdResponse(HttpResponse<Buffer> body) {
    /* TODO: Consider using JSON schema validation for this */

    int code = body.statusCode();
    Set<Integer> allowedCodes = Set.of(200, 403);
    
    if (!allowedCodes.contains(code)) {
      LOGGER.warn("Status code {}, Response body : {}", code, body.bodyAsString());
      return Future.failedFuture("Non " + allowedCodes.toString() + " status code sent by APD");
    }

    JsonObject json;

    try {
      json = body.bodyAsJsonObject();
    } catch (DecodeException e) {
      return Future.failedFuture("Invalid JSON sent by APD");
    }
    
    Boolean nullsInJson = json.stream().anyMatch(key -> key.getValue() == null);
    if (nullsInJson) {
      return Future.failedFuture("Nulls in APD response");
    }

    if (!json.containsKey(APD_RESP_TYPE) || !json.getString(APD_RESP_TYPE).matches(APD_URN_REGEX)) {
      return Future.failedFuture("No/invalid URN in APD response");
    }

    if (json.getString(APD_RESP_TYPE).equals(APD_URN_DENY)
        && (!json.containsKey(APD_RESP_DETAIL) || code != 403)) {
      return Future.failedFuture("Invalid status+URN or no detail in APD response");
    }

    if (json.getString(APD_RESP_TYPE).equals(APD_URN_DENY_NEEDS_INT)
        && (!json.containsKey(APD_RESP_DETAIL) || !json.containsKey(APD_RESP_SESSIONID)
            || code != 403)) {
      return Future.failedFuture("Invalid status+URN or no session ID in response");
    }

    if (json.getString(APD_RESP_TYPE).equals(APD_URN_ALLOW) && code != 200) {
      return Future.failedFuture("Invalid status+URN");
    }

    return Future.succeededFuture(json);
  }
}
