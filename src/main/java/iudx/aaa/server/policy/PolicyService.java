package iudx.aaa.server.policy;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The Policy Service.
 *
 * <h1>Policy Service</h1>
 *
 * <p>The Policy Service in the IUDX AAA Server defines the operations to be performed for Policy
 * creation, list, read, update, delete etc.
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
   * The verifyResourceAccess implements the resource access operation.
   *
   * @param request which is a JsonObject
   * @return Future of type JsonObject
   */
  Future<JsonObject> verifyResourceAccess(
      RequestToken request, DelegationInformation delegInfo, User user);

  /**
   * listDelegation implements the ability for a provider/consumer to view the delegations they have
   * created. Additionally, delegates may view the delegations assigned to them by
   * providers/consumers.
   *
   * @param user which is a {@link User} DataObject
   * @return Future of type JsonObject
   */
  Future<JsonObject> listDelegation(User user);

  /**
   * deleteDelegation implements the ability for a provider/consumer to delete the delegations they
   * have created.
   *
   * @param request which is a list of DeleteDelegationRequest objects
   * @param user which is a {@link User} DataObject an auth delegate
   * @return Future of type JsonObject
   */
  Future<JsonObject> deleteDelegation(List<DeleteDelegationRequest> request, User user);

  /**
   * createDelegation implements the ability for a provider/consumer to create delegations.
   *
   * @param request which is a list of CreateDelegationRequest objects
   * @param user which is a {@link User} DataObject an auth delegate
   * @return Future of type JsonObject
   */
  Future<JsonObject> createDelegation(List<CreateDelegationRequest> request, User user);

  /**
   * getDelegateEmails allows a trustee user to get all email addresses of valid delegates for user,
   * given the delegated role and the delegated resource server.
   *
   * @param user the trustee user calling the API
   * @param delegatorUserId the delegator's user ID
   * @param delegatedRole the delegated role
   * @param delegatedRsUrl the delegated RS URL
   * @return Future of type JsonObject
   */
  Future<JsonObject> getDelegateEmails(
      User user, String delegatorUserId, Roles delegatedRole, String delegatedRsUrl);
}
