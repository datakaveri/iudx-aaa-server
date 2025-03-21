package iudx.aaa.server.token;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.User;

/**
 * The Token Service.
 *
 * <h1>Token Service</h1>
 *
 * <p>The Token Service in the IUDX AAA Server defines the operations to be performed for creation,
 * list, read, update, delete tokens.
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
   * @return Future of type JsonObject
   */
  @GenIgnore
  static TokenService createProxy(Vertx vertx, String address) {
    return new TokenServiceVertxEBProxy(vertx, address);
  }

  /**
   * The createToken implements the token creation operation.
   *
   * @param requestToken which is a RequestToken
   * @param delegationInfo which contains info if delegate called the API, else is null
   * @param user which is User Object
   * @return Future of type JsonObject
   */
  Future<JsonObject> createToken(
      RequestToken requestToken, DelegationInformation delegationInfo, User user);

  /**
   * The revokeToken implements the token revocation operation.
   *
   * @param revokeToken which is a RevokeToken Object
   * @param user which is User Object
   * @return Future of type JsonObject
   */
  Future<JsonObject> revokeToken(RevokeToken revokeToken, User user);

  /**
   * The validateToken implements the token validation / introspect operation with the database.
   *
   * @param introspectToken which is a IntrospectToken object
   * @return Future of type JsonObject
   */
  Future<JsonObject> validateToken(IntrospectToken introspectToken);

  /**
   * Get an auth server JWT token. This token is used by the Auth server when calling other servers
   * to authenticate itself.
   *
   * @param audienceUrl the URL of the server to be called. The <i>aud</i> field in the token will
   *     contain this URL.
   * @return Future of type JsonObject
   */
  Future<JsonObject> getAuthServerToken(String audienceUrl);
}
