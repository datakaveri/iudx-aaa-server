package iudx.aaa.server.admin;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreateRsRequest;
import iudx.aaa.server.apiserver.ProviderUpdateRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The Admin Service.
 *
 * <h1>Admin Service</h1>
 *
 * <p>The Admin Service in the IUDX AAA Server defines the operations to be performed for Provider
 * approval, Organization creation etc.
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */
@VertxGen
@ProxyGen
public interface AdminService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return Future of type JsonObject
   */
  @GenIgnore
  static AdminService createProxy(Vertx vertx, String address) {
    return new AdminServiceVertxEBProxy(vertx, address);
  }

  /**
   * The getProviderRegistrations implements the operation to view Provider registrations.
   *
   * @param filter which is an instance of RoleStatus
   * @param user which is the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> getProviderRegistrations(RoleStatus filter, User user);

  /**
   * The updateProviderRegistrations implements the operation to approve/reject Provider
   * registrations.
   *
   * @param request which is a List of ProviderUpdateRequest data objects
   * @param user which is the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> updateProviderRegistrationStatus(
      List<ProviderUpdateRequest> request, User user);

  /**
   * The createResourceServer implements the ResourceServer creation operation.
   *
   * @param request CreateRsRequest data object
   * @param user the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> createResourceServer(CreateRsRequest request, User user);
}
