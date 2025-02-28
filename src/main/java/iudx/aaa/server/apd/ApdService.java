package iudx.aaa.server.apd;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.User;
import java.util.List;

/**
 * The Apd Service.
 *
 * <h1>Apd Service</h1>
 *
 * <p>The Apd Service in the IUDX AAA Server defines the operations to be performed for Access
 * Policy Domain management.
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
   * @return Future of type JsonObject
   */
  @GenIgnore
  static ApdService createProxy(Vertx vertx, String address) {
    return new ApdServiceVertxEBProxy(vertx, address);
  }

  /**
   * The listApd implements the operation to view Access Policy Domain registrations.
   *
   * <ul>
   *   <li>A user with <i>trustee</i> role will see all APDs they have registered.
   *   <li>A user who is the Auth Server admin will see all APD registrations.
   *   <li>A user with any other role will see all APDs in the <b>active</b> state.
   * </ul>
   *
   * @param user which is the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> listApd(User user);

  /**
   * The updateApd implements the operation to update Access Policy Domain status.
   *
   * <ul>
   *   <li>A user with <i>trustee</i> role may change the state from active -> inactive and inactive
   *       -> pending for any APD they have registered.
   *   <li>A user who is the Auth Server admin may change the state from pending -> active, active
   *       -> inactive and inactive -> active.
   * </ul>
   *
   * @param request which is a List of ApdUpdateRequest data objects
   * @param user which is the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> updateApd(List<ApdUpdateRequest> request, User user);

  /**
   * The createApd implements the operation to create an Access Policy Domain for a user who has the
   * trustee role.
   *
   * @param request CreateApdRequest data object
   * @param user the User object
   * @return Future of type JsonObject
   */
  Future<JsonObject> createApd(CreateApdRequest request, User user);

  /**
   * The callApd implements the operation to call an APD to verify if a user can access a resource
   * by belonging to a particular user class.
   *
   * @param apdContext a JsonObject containing the following keys with information required to call
   *     the APD
   *     <ul>
   *       <li><em>apdId</em> : The APD ID that is to be called
   *       <li><em>userId</em> : The user ID of the user requesting access
   *       <li><em>resource</em> : The resource cat ID for which access is needed
   *       <li><em>resSerUrl</em> : The resource server URL hosting the resource
   *       <li><em>userClass</em> : The user class set in the APD policy
   *       <li><em>providerId</em> : The user ID of the provider who owns the resource
   *       <li><em>constraints</em> : JSON object containing constraints set for the APD policy
   *     </ul>
   *
   * @return Future of type JsonObject
   */
  Future<JsonObject> callApd(JsonObject apdContext);

  /**
   * The getApdDetails implements the operation to get details of Access Policy Domains.
   *
   * @param apdUrl a list of valid APD urls
   * @param apdIds a list of valid APD IDs
   * @return Future of type JsonObject
   */
  Future<JsonObject> getApdDetails(List<String> apdUrl, List<String> apdIds);
}
