package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_OWNER;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.RESP_OWNER_USER_ID;
import static iudx.aaa.server.apd.Constants.SQL_INSERT_APD_IF_NOT_EXISTS;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_REGISTERED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;

import com.google.common.net.InternetDomainName;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.registration.RegistrationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The APD (Access Policy Domain) Verticle.
 * <h1>APD Verticle</h1>
 * <p>
 * The APD Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.apd.ApdService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 */
public class ApdServiceImpl implements ApdService {

  private static final Logger LOGGER = LogManager.getLogger(ApdServiceImpl.class);

  private static String AUTH_SERVER_URL;
  private PgPool pool;
  private ApdWebClient apdWebClient;
  private RegistrationService registrationService;

  public ApdServiceImpl(PgPool pool, ApdWebClient apdWebClient, RegistrationService regService,
      JsonObject options) {
    this.pool = pool;
    this.apdWebClient = apdWebClient;
    this.registrationService = regService;
    AUTH_SERVER_URL = options.getString(CONFIG_AUTH_URL);
  }

  @Override
  public ApdService listApd(User user, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService updateApd(List<ApdUpdateRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ApdService createApd(CreateApdRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (!user.getRoles().contains(Roles.TRUSTEE)) {
      Response resp = new ResponseBuilder().type(URN_INVALID_ROLE).title(ERR_TITLE_NOT_TRUSTEE)
          .detail(ERR_DETAIL_NOT_TRUSTEE).status(403).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    String url = request.getUrl().toLowerCase();
    String name = request.getName();
    UUID trusteeId = UUID.fromString(user.getUserId());

    if (!InternetDomainName.isValid(url)) {
      Response resp = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_INVALID_DOMAIN)
          .detail(ERR_DETAIL_INVALID_DOMAIN).status(400).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    Tuple tuple = Tuple.of(name, url, trusteeId);
    Future<Boolean> isApdOnline = apdWebClient.checkApdExists(url);

    Future<UUID> apdId = isApdOnline
        .compose(success -> pool.withTransaction(
            conn -> conn.preparedQuery(SQL_INSERT_APD_IF_NOT_EXISTS).execute(tuple)))
        .compose(res -> {
          if (res.size() == 0) {
            return Future.failedFuture(new ComposeException(409, URN_ALREADY_EXISTS.toString(),
                ERR_TITLE_EXISTING_DOMAIN, ERR_DETAIL_EXISTING_DOMAIN));
          }
          return Future.succeededFuture(res.iterator().next().getUUID(0));
        });

    Future<Map<String, JsonObject>> trusteeDetailsFut =
        apdId.compose(success -> getTrusteeDetails(List.of(trusteeId.toString())));

    trusteeDetailsFut.onSuccess(trusteeDetails -> {
      JsonObject response = new JsonObject();
      response.put(RESP_APD_ID, apdId.result().toString()).put(RESP_APD_NAME, name)
          .put(RESP_APD_URL, url).put(RESP_APD_STATUS, ApdStatus.PENDING.toString().toLowerCase());

      JsonObject ownerDetails = trusteeDetails.get(trusteeId.toString());
      ownerDetails.put(RESP_OWNER_USER_ID, trusteeId.toString());
      response.put(RESP_APD_OWNER, ownerDetails);

      LOGGER.info("APD registered with id : " + apdId.result().toString());

      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
          .title(SUCC_TITLE_REGISTERED_APD).objectResults(response).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
    }).onFailure(e -> {
      if (e instanceof ComposeException) {
        ComposeException exp = (ComposeException) e;
        handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
        return;
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });
    
    return this;
  }

  @Override
  public ApdService getApdDetails(List<String> apdIds, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Calls RegistrationService.getUserDetails to specifically get details of trustees.
   * 
   * @param userIds List of strings of user IDs
   * @return a future of a Map, mapping the string user ID to a JSON object containing the user
   *         details
   */
  private Future<Map<String, JsonObject>> getTrusteeDetails(List<String> userIds) {
    Promise<Map<String, JsonObject>> promise = Promise.promise();
    Promise<JsonObject> regServicePromise = Promise.promise();
    Future<JsonObject> response = regServicePromise.future();

    registrationService.getUserDetails(userIds, regServicePromise);
    response.onSuccess(obj -> {
      Map<String, JsonObject> details = obj.stream().collect(
          Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
      promise.complete(details);
    }).onFailure(err -> {
      LOGGER.error(err.getMessage());
      promise.fail("Get user details failed");
    });

    return promise.future();
  }

}
