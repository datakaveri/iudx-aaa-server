package iudx.aaa.server.tip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.postgres.client.PostgresClient;

/**
 * The TIP Service Implementation.
 * <h1>TIP Service Implementation</h1>
 * <p>
 * The TIP Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.tip.TIPService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class TIPServiceImpl implements TIPService {

  private static final Logger LOGGER = LogManager.getLogger(TIPServiceImpl.class);

  private PostgresClient pgClient;

  public TIPServiceImpl(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  @Override
  public TIPService validateToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }
}
