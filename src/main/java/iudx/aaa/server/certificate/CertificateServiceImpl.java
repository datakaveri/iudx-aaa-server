package iudx.aaa.server.certificate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.postgres.client.PostgresClient;

/**
 * The Certificate Service Implementation.
 * <h1>Certificate Service Implementation</h1>
 * <p>
 * The Certificate Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.certificate.CertificateService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class CertificateServiceImpl implements CertificateService {

  private static final Logger LOGGER = LogManager.getLogger(CertificateServiceImpl.class);

  private PostgresClient pgClient;

  public CertificateServiceImpl(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  @Override
  public CertificateService validateCertificate(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

  @Override
  public CertificateService generateCertificate(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

  @Override
  public CertificateService createCSR(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }

  @Override
  public CertificateService updateCSR(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }
}
