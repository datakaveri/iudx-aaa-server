package iudx.aaa.server.token;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.registration.RegistrationService;

/**
 * The Token Service.
 * <h1>Token Service</h1>
 * <p>
 * The Token Service in the IUDX AAA Server defines the operations to be performed for creation,
 * list, read, update, delete tokens.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface TokenService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return TokenServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static TokenService createProxy(Vertx vertx, String address) {
    return new TokenServiceVertxEBProxy(vertx, address);
  }

  /**
   * The createToken implements the token creation operation.
   * 
   * @param requestToken which is a RequestToken
   * @param handler which is a Request Handler
   * @param roleList which is a JsonArray
   * @return TokenService which is a Service
   */

  @Fluent
  TokenService createToken(RequestToken requestToke, JsonArray roleList, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The revokeToken implements the token revocation operation.
   * 
   * @param revokeToken which is a RevokeToken Object
   * @param user which is User Object
   * @param handler which is a Request Handler
   * @return TokenService which is a Service
   */
  
  @Fluent
  TokenService revokeToken(RevokeToken revokeToken, User user, Handler<AsyncResult<JsonObject>> handler);


  /**
   * The validateToken implements the token validation / introspect operation with the database.
   * 
   * @param introspectToken which is a IntrospectToken object
   * @param handler which is a Request Handler
   * @return TokenService which is a Service
   */

  @Fluent
  TokenService validateToken(IntrospectToken introspectToken, Handler<AsyncResult<JsonObject>> handler);

}
