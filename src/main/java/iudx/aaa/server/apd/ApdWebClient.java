package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_READ_USERCLASSES_API;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_RESPOND;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import static iudx.aaa.server.apiserver.util.Urn.*;
import iudx.aaa.server.registration.ComposeException;

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
    Response failureResponse = new ResponseBuilder().type(URN_INVALID_INPUT)
        .title(ERR_TITLE_APD_NOT_RESPOND).detail(ERR_DETAIL_APD_NOT_RESPOND).status(400).build();

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
}
