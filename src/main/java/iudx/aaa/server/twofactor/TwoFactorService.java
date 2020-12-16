package iudx.aaa.server.twofactor;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.token.TokenService;

/**
 * The TwoFactor Service.
 * <h1>TwoFactor Service</h1>
 * <p>
 * The TwoFactor Service in the IUDX AAA Server defines the operations to be performed for OTP
 * generation and validation
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface TwoFactorService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return TwoFactorServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static TwoFactorService createProxy(Vertx vertx, String address) {
    return new TwoFactorServiceVertxEBProxy(vertx, address);
  }

  /**
   * The generateOTP implements the OTP generation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return TokenService which is a Service
   */

  @Fluent
  TwoFactorService generateOTP(JsonObject request, Handler<AsyncResult<JsonObject>> handler);


  /**
   * The validateOTP implements the OTP validation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return TokenService which is a Service
   */

  @Fluent
  TwoFactorService validateOTP(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

}
