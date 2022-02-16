package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The APD (Access Policy Domain) Verticle.
 * <h1>APD Verticle</h1>
 * <p>
 * The APD Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.apd.ApdService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 */
public class ApdServiceImpl implements ApdService {

  private static String AUTH_SERVER_URL;
  private PgPool pool;
  private ApdWebClient apdWebClient;

  public ApdServiceImpl(PgPool pool, ApdWebClient apdWebClient, JsonObject options) {
    this.pool = pool;
    this.apdWebClient = apdWebClient;
    AUTH_SERVER_URL = options.getString(CONFIG_AUTH_URL);
  }

  @Override
  public ApdService listApd(User user, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService updateApd(List<ApdUpdateRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService createApd(CreateApdRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService getApdDetails(List<String> apdIds, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

}
