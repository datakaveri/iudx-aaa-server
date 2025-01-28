package iudx.aaa.server.registration;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.AddRolesRequest;
import iudx.aaa.server.apiserver.ResetClientSecretRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import java.util.List;
import java.util.Set;

/**
 * The Registration Service.
 *
 * <h1>Registration Service</h1>
 *
 * <p>The Registration Service in the IUDX AAA Server defines the operations to be performed for
 * User profile creation, updation, read, as well as listing Organizations
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
   * @return Future of type JsonObject
   */
  @GenIgnore
  static RegistrationService createProxy(Vertx vertx, String address) {
    return new RegistrationServiceVertxEBProxy(vertx, address);
  }

  /**
   * addRoles implements adding of roles to the user.
   *
   * @param request the request body in the form of {@link AddRolesRequest} data object
   * @param user the User object i.e. the user calling the API
   * @return Future of type JsonObject
   */
  Future<JsonObject> addRoles(AddRolesRequest request, User user);

  /**
   * The listUser implements the user list operation.
   *
   * @param user the User object i.e. the user calling the API
   * @return Future of type JsonObject
   */
  Future<JsonObject> listUser(User user);

  /**
   * The resetClientSecret implements client secret regeneration.
   *
   * @param request the request body in the form of UpdateProfileRequest data object
   * @return Future of type JsonObject
   */
  Future<JsonObject> resetClientSecret(ResetClientSecretRequest request, User user);

  /**
   * The listResourceServer implements listing resource servers.
   *
   * @return Future of type JsonObject
   */
  Future<JsonObject> listResourceServer();

  /**
   * The getUserDetails implements getting user details. Other services may call this service to get
   * email and names of users based on their user ID.
   *
   * @param userIds list of user IDs as String in UUID format
   * @return Future of type JsonObject
   */
  Future<JsonObject> getUserDetails(List<String> userIds);

  /**
   * The findUsersByEmail implements finding users by their email ID from Keycloak. Users who are
   * found are then inserted into the database if they were not already there (specifically into the
   * users table).
   *
   * @param emailIds set of email IDs
   * @return Future of type JsonObject
   */
  Future<JsonObject> findUserByEmail(Set<String> emailIds);

  /**
   * Get default client credentials. A user can fetch their automatically created client ID and
   * client secret with the client name<em>default</em> <b>once</b>. Once the user has requested
   * them, the client secret cannot be obtained again using this API.
   *
   * @param user the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> getDefaultClientCredentials(User user);

  /**
   * Search for a user done by trustee given email <b>OR</b> user ID, role and resource server
   * associated with the role on the COP.
   *
   * @param user the trustee user calling the API
   * @param searchString the email address OR user ID - UUID regex matching is done to check which
   *     is being sent
   * @param role the role
   * @param resourceServerUrl the resource server associated with the rol
   * @return Future of type JsonObject
   */
  Future<JsonObject> searchUser(
      User user, String searchString, Roles role, String resourceServerUrl);
}
