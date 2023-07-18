package iudx.aaa.server.registration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.RegistrationRequest;
import iudx.aaa.server.apiserver.UpdateProfileRequest;
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
   * @param request the request body in the form of RegistrationRequest data object
   * @param user the User object i.e. the user calling the API
   * @param handler the request handler which returns a JsonObject
   * @return Registration Service which is a service
   */
  @Fluent
  RegistrationService createUser(RegistrationRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listUser implements the user list operation. It additionally implements a search function
   * for providers, admins and auth delegates to search for a user's details from their email
   * address and role.
   * 
   * @param user the User object i.e. the user calling the API
   * @param searchUserDetails a JSON object with the email and role
   * @param authDelegateDetails a JSON object with the auth delegate details if an auth delegate
   *        calls the API
   * @param handler the request handler which returns a JsonObject
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listUser(User user, JsonObject searchUserDetails,
      JsonObject authDelegateDetails, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The updateUser implements the user update operation. Currently role addition and client secret
   * regeneration is allowed.
   * 
   * @param request the request body in the form of UpdateProfileRequest data object
   * @param handler the request handler which returns a JsonObject
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService updateUser(UpdateProfileRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listOrganization implements listing organzations.
   * 
   * @param handler the request handler which returns a JsonObject
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService listOrganization(Handler<AsyncResult<JsonObject>> handler);

  /**
   * The getUserDetails implements getting user details. Other services may call this service to get
   * email and names of users based on their user ID.
   * 
   * @param userIds list of user IDs as String in UUID format
   * @param handler the request handler which returns a JsonObject
   * @return RegistrationService which is a Service
   */

  @Fluent
  RegistrationService getUserDetails(List<String> userIds,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The findUsersByEmail implements finding users by their email ID from Keycloak. Users who are
   * found are then inserted into the database if they were not already there (specifically into the
   * users table).
   * 
   * @param emailIds set of email IDs
   * @param handler the request handler which returns a JsonObject
   * @return RegistrationService which is a Service
   */
  @Fluent
  RegistrationService findUserByEmail(Set<String> emailIds,
      Handler<AsyncResult<JsonObject>> handler);
}
