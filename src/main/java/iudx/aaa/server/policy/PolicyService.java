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
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.UpdatePolicyNotification;
import iudx.aaa.server.apiserver.User;

import java.util.List;

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
   * @param request which is a list of DataObject
   * @param user which is a DataObject
   * @param handler handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService createPolicy(List<CreatePolicyRequest> request , User user , Handler<AsyncResult<JsonObject>> handler);

  /**
   * The deletePolicy implements the policy delete operation.
   * 
   * @param request which is a JsonArray
   * @param user which is a DataObject
   * @param handler handler which is a Request Handler
   * @return PolicyService which is a Service
   */
  
  @Fluent
  PolicyService deletePolicy(JsonArray request, User user, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The listPolicy implements the policy list operation.
   * 
   * @param user which is a DataObject
   * @param data which is a JsonObject
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService listPolicy(User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler);


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
  
  /**
   * The createPolicyNotification implements the creating request for user policies.
   * 
   * @param request which is a list of {@link CreatePolicyNotification} DataObject
   * @param user which is a {@link User} DataObect
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */
  
  @Fluent
  PolicyService createPolicyNotification(List<CreatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler);
  
  /**
   * The listPolicyNotification implements the listing request for user and provider/delegate.
   * 
   * @param user which is a {@link User} DataObect
   * @Param data which is a {@link JsonObject}
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */

  @Fluent
  PolicyService listPolicyNotification(User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The updatePolicyNotification implements the updating resources access/status by provider/delegate.
   * 
   * @param request which is a list of {@link UpdatePolicyNotification} DataObject
   * @param user which is a {@link User} DataObect
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */
  
  @Fluent
  PolicyService updatelistPolicyNotification(List<UpdatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler);

  /**
   * listDelegation implements the ability for a provider to view the delegations they have created.
   * It allows auth delegates to perform the same on behalf of a provider (although an auth delegate
   * may not view auth delegate-related information). Additionally, delegates may view the
   * delegations assigned to them by providers.
   * 
   * @param user which is a {@link User} DataObject
   * @param authDelegateDetails which contains details of the provider, etc. in case the caller is
   *        an auth delegate
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */
  @Fluent
  PolicyService listDelegation(User user, JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * deleteDelegation implements the ability for a provider to delete the delegations they have
   * created. It allows auth delegates to perform the same on behalf of a provider (although an auth
   * delegate may not delete aauth delegate-related information).
   * 
   * @param request which is a list of DeleteDelegationRequest objects
   * @param user which is a {@link User} DataObject
   * @param authDelegateDetails which contains details of the provider, etc. in case the caller is
   *        an auth delegate
   * @param handler which is a Request Handler
   * @return PolicyService which is a Service
   */
  @Fluent
  PolicyService deleteDelegation(List<DeleteDelegationRequest> request, User user,
      JsonObject authDelegateDetails, Handler<AsyncResult<JsonObject>> handler);
}
