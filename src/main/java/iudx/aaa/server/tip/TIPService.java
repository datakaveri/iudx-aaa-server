package iudx.aaa.server.tip;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The TIP Service.
 * <h1>TIP Service</h1>
 * <p>
 * The TIP Service in the IUDX AAA Server defines the operations to be performed for token
 * introspection.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface TIPService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return TIPServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static TIPService createProxy(Vertx vertx, String address) {
    return new TIPServiceVertxEBProxy(vertx, address);
  }

  /**
   * The validateToken implements the token validation / introspect operation with the database.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return TIPService which is a Service
   */

  @Fluent
  TIPService validateToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

}
