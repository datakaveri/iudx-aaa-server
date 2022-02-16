package iudx.aaa.server.apd;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

/**
 * 
 * ApdWebClient is used to perform HTTP calls to the APD. It is a separate class so that it can be
 * easily mocked when testing.
 *
 */
public class ApdWebClient {
  private WebClient webClient;

  public ApdWebClient(WebClient wc) {
    this.webClient = wc;
  }

  /**
   * The function is used to check if the Access Policy Domain exists. This is needed when the APD
   * is being registered. The read user class API is called on the URL provided.
   * 
   * @param url The URL supplied during APD registration
   * @return a boolean future indicating the result
   */
  public Future<Boolean> checkApdExists(String url) {
    return null;
  }
}
