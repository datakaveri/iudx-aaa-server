package iudx.aaa.server.twofactor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.postgres.client.PostgresClient;

/**
 * The TwoFactor Service Implementation.
 * <h1>TwoFactor Service Implementation</h1>
 * <p>
 * The TwoFactor Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.twofactor.TwoFactorService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class TwoFactorServiceImpl implements TwoFactorService{

  private static final Logger LOGGER = LogManager.getLogger(TwoFactorServiceImpl.class);

  private PostgresClient pgClient;

  public TwoFactorServiceImpl(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  @Override
  public TwoFactorService generateOTP(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

  @Override
  public TwoFactorService validateOTP(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

}
