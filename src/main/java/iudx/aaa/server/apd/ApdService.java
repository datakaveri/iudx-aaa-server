package iudx.aaa.server.apd;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The Apd Service.
 * <h1>Apd Service</h1>
 * <p>
 * The Apd Service in the IUDX AAA Server defines the operations to be performed for Access Policy
 * Domain management.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface ApdService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return ApdServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static ApdService createProxy(Vertx vertx, String address) {
    return new ApdServiceVertxEBProxy(vertx, address);
  }

  /**
   * The listApd implements the operation to view Access Policy Domain registrations.
   * <ul>
   * <li>A user with <i>trustee</i> role will see all APDs they have registered.</li>
   * <li>A user who is the Auth Server admin will see all APD registrations.</li>
   * <li>A user with any other role will see all APDs in the <b>active</b> state.</li>
   * </ul>
   * 
   * @param user which is the User object
   * @param handler which is a Request Handler
   * @return ApdService which is a Service
   */
  @Fluent
  ApdService listApd(User user, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The updateApd implements the operation to update Access Policy Domain status.
   * 
   * <ul>
   * <li>A user with <i>trustee</i> role may change the state from active -> inactive and inactive
   * -> pending for any APD they have registered.</li>
   * <li>A user who is the Auth Server admin may change the state from pending -> active, active ->
   * inactive and inactive -> active.</li>
   * </ul>
   * 
   * @param request which is a List of ApdUpdateRequest data objects
   * @param user which is the User object
   * @param handler which is a Request Handler
   * @return ApdService which is a Service
   */
  @Fluent
  ApdService updateApd(List<ApdUpdateRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The createApd implements the operation to create an Access Policy Domain for a user who has the
   * trustee role.
   * 
   * @param request CreateApdRequest data object
   * @param user the User object
   * @param handler which is a request handler
   * @return ApdService which is a Service
   */
  @Fluent
  ApdService createApd(CreateApdRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The getApdDetails implements the operation to get details of Access Policy Domains.
   * 
   * @param apdIds a list of valid APD IDs
   * @param handler which is a request handler
   * @return ApdService which is a Service
   */
  @Fluent
  ApdService getApdDetails(List<String> apdIds, Handler<AsyncResult<JsonObject>> handler);
}
