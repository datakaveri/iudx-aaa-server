package iudx.aaa.server.policy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.User;

import java.util.List;
import java.util.UUID;

/**
 * The Policy Service.
 * <h1>Policy Service</h1>
 * <p>
 * The Policy Service in the IUDX AAA Server defines the operations to be performed for Policy
 * creation, list, read, update, delete etc.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface PolicyService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return PolicyServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static PolicyService createProxy(Vertx vertx, String address) {
    return new PolicyServiceVertxEBProxy(vertx, address);
  }

  /**
   * The createPolicy implements the policy creation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService createPolicy(List<CreatePolicyRequest> request , User user , Handler<AsyncResult<JsonObject>> handler);

  /**
   * The deletePolicy implements the policy delete operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService deletePolicy(JsonArray request, User user, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listPolicy implements the policy list operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService listPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The verifyPolicy implements the policy list operation.
   *
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService verifyPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The setDefaultProviderPolicies implements setting default provider policies when they are
   * approved by an auth server admin.
   *
   * @param userIds a list of Strings (as UUIDs) of user IDs
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService setDefaultProviderPolicies(List<String> userIds,
      Handler<AsyncResult<JsonObject>> handler);
}
