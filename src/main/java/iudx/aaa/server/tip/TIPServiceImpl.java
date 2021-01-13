package iudx.aaa.server.tip;

import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.hash.Hashing;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.aaa.server.postgres.client.PostgresClient;
import iudx.aaa.server.tip.dto.IntrospectRequest;
import iudx.aaa.server.tip.repository.TokenInterospectRepo;
import iudx.aaa.server.tip.service.TokenIntersopectService;

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
  private TokenInterospectRepo repository;
  private TokenIntersopectService service;

  public TIPServiceImpl(PostgresClient pgClient) {
    this.pgClient = pgClient;
    this.repository = new TokenInterospectRepo(this.pgClient);
    this.service = new TokenIntersopectService(this.repository);
  }

  @Override
  public TIPService validateToken(IntrospectRequest interospectRequest,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    String token = interospectRequest.getToken();
    if (token.isEmpty()) {
      // return error/400 bad request to caller (EB or API)
    }
    JsonObject response = new JsonObject();
    service.validateAccess(token).onComplete(accessValidationHandler -> {
      if (accessValidationHandler.succeeded()) {
        JsonObject result = accessValidationHandler.result();
        response.put("consumer", result.getString("consumers"));
        response.put("expiry", result.getString("expiry"));
        response.put("roles", result.getJsonArray("roles"));
        response.put("status", Boolean.TRUE);
        handler.handle(Future.succeededFuture(response));
      } else {
        response.put("status", Boolean.FALSE);
        handler.handle(Future.failedFuture(response.toString()));
      }
    });
    return this;
  }


  private String getSHA256(String value) {
    return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
  }


}
