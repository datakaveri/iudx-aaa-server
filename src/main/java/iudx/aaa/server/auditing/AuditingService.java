package iudx.aaa.server.auditing;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The Auditing Service.
 *
 * <h1>Auditing Service</h1>
 *
 * <p>The Auditing Service in the IUDX AAA Server implements auditing of significant actions.
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */
@ProxyGen
@VertxGen
public interface AuditingService {

  @GenIgnore
  static AuditingService createProxy(Vertx vertx, String address) {
    return new AuditingServiceVertxEBProxy(vertx, address);
  }

  @Fluent
  AuditingService executeWriteQuery(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  AuditingService executeReadQuery(JsonObject request, Handler<AsyncResult<JsonObject>> handler);
}
