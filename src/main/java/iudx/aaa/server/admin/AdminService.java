package iudx.aaa.server.admin;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreateOrgRequest;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The Admin Service.
 * <h1>Admin Service</h1>
 * <p>
 * The Admin Service in the IUDX AAA Server defines the operations to be performed for Provider
 * approval, Organization creation etc.
 * </p>
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
   * @return AdminServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static AdminService createProxy(Vertx vertx, String address) {
    return new AdminServiceVertxEBProxy(vertx, address);
  }

  /**
   * The getProviderRegistrations implements the operation to view Provider registrations.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return AdminService which is a Service
   */
  @Fluent
  AdminService getProviderRegistrations(String filter, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The updateProviderRegistrations implements the operation to approve/reject Provider
   * registrations.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return AdminService which is a Service
   */
  @Fluent
  AdminService updateProviderRegistrationStatus(List<ProviderUpdateRequest> request,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The createOrganization implements the Organization creation operation.
   * 
   * @param request CreateOrgRequest data object
   * @param user the User object
   * @param handler which is a request handler
   * @return AdminService which is a Service
   */
  @Fluent
  AdminService createOrganization(CreateOrgRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);
}
