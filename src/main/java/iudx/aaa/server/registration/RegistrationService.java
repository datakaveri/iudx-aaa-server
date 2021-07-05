package iudx.aaa.server.registration;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.RegistrationRequest;
import iudx.aaa.server.apiserver.User;

/**
 * The Registration Service.
 * <h1>Registration Service</h1>
 * <p>
 * The Registration Service in the IUDX AAA Server defines the operations to be performed for User
 * profile creation, updation, read, as well as listing Organizations
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface RegistrationService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return RegistrationServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static RegistrationService createProxy(Vertx vertx, String address) {
    return new RegistrationServiceVertxEBProxy(vertx, address);
  }

  /**
   * createUser implements creation of user profile operation.
   * 
   * @param request the request body in the form of RegistrationRequestDO
   * @param user the User object i.e. the user calling the API
   * @param handler the request handler which returns a JsonObject
   * @return Registration Service which is a service
   */
  @Fluent
  RegistrationService createUser(RegistrationRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listUser implements the user list operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listUser(User user, Handler<AsyncResult<JsonObject>> handler);


  /**
   * The updateUser implements the user update operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService updateUser(RegistrationRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listOrganization implements listing organzations.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listOrganization(Handler<AsyncResult<JsonObject>> handler);

}
