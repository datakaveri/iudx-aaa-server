package iudx.aaa.server.registration;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The Registration Service.
 * <h1>Registration Service</h1>
 * <p>
 * The Registration Service in the IUDX AAA Server defines the operations to be performed for User /
 * Organization creation, list, read, update, delete etc.
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
   * The createUser implements the user creation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService createUser(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listUser implements the user list operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listUser(JsonObject request, Handler<AsyncResult<JsonObject>> handler);


  /**
   * The updateUser implements the user update operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService updateUser(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The addRole implements the adding user to a role operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService addRole(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The removeRole implements the removing a user from a role operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService removeRole(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The createOrganization implements the creating an organization operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService createOrganization(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listOrganization implements the listing of an organization operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listOrganization(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

}
