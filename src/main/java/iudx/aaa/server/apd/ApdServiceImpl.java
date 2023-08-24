package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.APD_NOT_ACTIVE;
import static iudx.aaa.server.apd.Constants.APD_REQ_CONTEXT;
import static iudx.aaa.server.apd.Constants.APD_REQ_ITEM;
import static iudx.aaa.server.apd.Constants.APD_REQ_OWNER;
import static iudx.aaa.server.apd.Constants.APD_REQ_USER;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_LINK;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_APD_INTERAC;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CAT_ID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_LINK;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SESSIONID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_STATUS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SUCCESS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_UUID;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_REGISTERED;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_REGISTERED;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_CANT_CHANGE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_APDID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_REQUEST;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_REQUEST_ID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_POLICY_EVAL_FAILED;
import static iudx.aaa.server.apd.Constants.GET_APDINFO_ID;
import static iudx.aaa.server.apd.Constants.GET_APDINFO_URL;
import static iudx.aaa.server.apd.Constants.INTERNALERROR;
import static iudx.aaa.server.apd.Constants.LIST_AUTH_QUERY;
import static iudx.aaa.server.apd.Constants.LIST_USER_QUERY;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.SQL_GET_APDS_BY_ID_COS_ADMIN;
import static iudx.aaa.server.apd.Constants.SQL_GET_APD_URL_STATUS;
import static iudx.aaa.server.apd.Constants.SQL_INSERT_APD_IF_NOT_EXISTS;
import static iudx.aaa.server.apd.Constants.SQL_UPDATE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_APD_READ;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_REGISTERED_APD;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_UPDATED_APD;
import static iudx.aaa.server.apd.Constants.UUID_REGEX;
import static iudx.aaa.server.apiserver.util.Urn.*;

import com.google.common.net.InternetDomainName;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ApdInfoObj;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.token.TokenService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
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

  private PgPool pool;
  private ApdWebClient apdWebClient;
  private RegistrationService registrationService;
  private TokenService tokenService;

  public ApdServiceImpl(PgPool pool, ApdWebClient apdWebClient, RegistrationService regService,
      TokenService tokService, JsonObject options) {
    this.pool = pool;
    this.apdWebClient = apdWebClient;
    this.registrationService = regService;
    this.tokenService = tokService;
  }

  /**
   * authAdminStates determines what kind of state changes each user
   * can make. See javadoc for updateApd for allowed states. Currently, each starting state is
   * present only once, so we can have Map<ApdStatus, ApdStatus>. If this changes, we can have
   * Map<ApdStatus, Set<ApdStatus>>. 
   * Since we use '==' for equality checking of ApdStatus enum, no NPE is thrown.
   */
  static private Map<ApdStatus, ApdStatus> authAdminStates =
      Map.of(ApdStatus.ACTIVE, ApdStatus.INACTIVE, ApdStatus.INACTIVE, ApdStatus.ACTIVE);

  @Override
  public ApdService listApd(User user, Handler<AsyncResult<JsonObject>> handler) {
    if (user.getRoles().isEmpty()) {
      Response r =
              new ResponseBuilder()
                      .status(404)
                      .type(URN_MISSING_INFO)
                      .title(ERR_TITLE_NO_APPROVED_ROLES)
                      .detail(ERR_DETAIL_NO_APPROVED_ROLES)
                      .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }
    
    String query;
    Tuple tuple;
    if(user.getRoles().contains(Roles.COS_ADMIN)) {
      query = LIST_AUTH_QUERY;
      tuple = Tuple.of(ApdStatus.ACTIVE.toString(),ApdStatus.INACTIVE.toString());
    }
    else {
      query = LIST_USER_QUERY;
      tuple = Tuple.of(ApdStatus.ACTIVE.toString());
    }

    Collector<Row, ?, List<String>> ApdIdCollector =
        Collectors.mapping(row -> row.getUUID("id").toString(), Collectors.toList());

    Future<List<String>> apdIds = pool
        .withConnection(conn -> conn.preparedQuery(query).collecting(ApdIdCollector).execute(tuple))
        .map(SqlResult::value);
                    

    Future<JsonObject> apdDetails =
            apdIds.compose(
                    ids -> {
                      if(ids.isEmpty())
                        return Future.succeededFuture(new JsonObject());

                      Promise<JsonObject> response = Promise.promise();
                      Promise<JsonObject> promise = Promise.promise();
                      getApdDetails(new ArrayList<>(), ids, promise);
                      promise
                              .future()
                              .onComplete(
                                      ar -> {
                                        response.complete(ar.result());
                                      });

                      return response.future();
                    });

    apdDetails
            .compose(
                    details -> {
                      if(details.isEmpty())
                      {
                        return Future.succeededFuture(new JsonArray());
                      }
                      JsonArray response = new JsonArray();
                        apdIds.result().forEach(id->{
                        JsonObject result = details.getJsonObject(id);
                        response.add(result);
                      });
                      return Future.succeededFuture(response);
                    })
            .onSuccess(
                    ar -> {
                      Response resp =
                              new ResponseBuilder()
                                      .status(200)
                                      .type(URN_SUCCESS)
                                      .title(SUCC_TITLE_APD_READ)
                                      .arrayResults(ar)
                                      .build();
                      handler.handle(Future.succeededFuture(resp.toJson()));
                    })
            .onFailure(
                    e -> {
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
  public ApdService updateApd(List<ApdUpdateRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (!user.getRoles().contains(Roles.COS_ADMIN)) {
      Response r = new ResponseBuilder().status(401).type(URN_INVALID_ROLE)
          .title(ERR_TITLE_NO_COS_ADMIN_ROLE).detail(ERR_DETAIL_NO_COS_ADMIN_ROLE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    /*
     * Check for duplicate apd IDs (same apd ID, different status, OpenAPI can't catch this) by
     * checking if an apd ID is already added to the `apdIds` set
     */
    Set<UUID> apdIds = new HashSet<UUID>();
    List<UUID> requestedApdIds =
        request.stream().map(r -> UUID.fromString(r.getApdId())).collect(Collectors.toList());
    List<UUID> duplicates =
        requestedApdIds.stream().filter(id -> apdIds.add(id) == false).collect(Collectors.toList());

    if (!duplicates.isEmpty()) {
      String firstOffendingId = duplicates.get(0).toString();
      Response resp = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_DUPLICATE_REQ)
          .detail(firstOffendingId).status(400).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    Collector<Row, ?, Map<UUID, JsonObject>> collector =
        Collectors.toMap(row -> row.getUUID("id"), row -> row.toJson());

    Future<Map<UUID, JsonObject>> queryResult = pool
          .withConnection(conn -> conn.preparedQuery(SQL_GET_APDS_BY_ID_COS_ADMIN).collecting(collector)
              .execute(Tuple.of(apdIds.toArray(UUID[]::new))))
          .map(res -> res.value());

    Future<Void> validateStatus = queryResult.compose(map -> {
      Set<UUID> queriedIds = map.keySet();

      if (queriedIds.size() != apdIds.size()) {
        apdIds.removeAll(queriedIds);
        String firstOffender = apdIds.iterator().next().toString();
        return Future.failedFuture(new ComposeException(400, URN_INVALID_INPUT.toString(),
            ERR_TITLE_INVALID_APDID, firstOffender));
      }

      Map<UUID, ApdStatus> currentStatus = map.entrySet().stream().collect(Collectors
          .toMap(i -> i.getKey(), i -> ApdStatus.valueOf(i.getValue().getString("status"))));

      Map<UUID, ApdStatus> desiredStatus = request.stream()
          .collect(Collectors.toMap(i -> UUID.fromString(i.getApdId()), i -> i.getStatus()));

        return checkValidStatusChange(authAdminStates, currentStatus, desiredStatus);
    });


    validateStatus.compose(success -> {
      List<Tuple> tuple =
          request.stream().map(req -> Tuple.of(req.getStatus(), UUID.fromString(req.getApdId())))
              .collect(Collectors.toList());

      return pool
          .withTransaction(conn -> conn.preparedQuery(SQL_UPDATE_APD_STATUS).executeBatch(tuple));

    }).onSuccess(updated -> {
      JsonArray response = new JsonArray();
      Map<UUID, JsonObject> apdDetails = queryResult.result();

      for (ApdUpdateRequest req : request) {
        UUID apdId = UUID.fromString(req.getApdId());
        JsonObject obj = apdDetails.get(apdId);

        obj.remove(RESP_APD_STATUS);
        obj.put(RESP_APD_STATUS, req.getStatus().toString().toLowerCase());

        response.add(obj);
        LOGGER.info("APD status updated : " + apdId.toString());
      }

      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
          .title(SUCC_TITLE_UPDATED_APD).arrayResults(response).build();
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

  /**
   * Check if list of APDs in some state A can be changed to a state B based on privileges.
   * 
   * @param allowedStates a Map of ApdStatus to ApdStatus that determines allowed state changes
   * @param currentStatus a Map of APD IDs (UUIDs) to the current state they are in (ApdStatus)
   * @param desiredStatus a Map of APD IDs (UUIDs) to the state they require to be changed to
   *        (ApdStatus)
   * @return a void Future. If a state change is not allowed, a ComposeException is thrown with the
   *         APD ID in the response detail
   */
  private Future<Void> checkValidStatusChange(Map<ApdStatus, ApdStatus> allowedStates,
      Map<UUID, ApdStatus> currentStatus, Map<UUID, ApdStatus> desiredStatus) {
    Promise<Void> p = Promise.promise();

    for (Entry<UUID, ApdStatus> entry : currentStatus.entrySet()) {

      ApdStatus current = entry.getValue();
      ApdStatus desired = desiredStatus.get(entry.getKey());

      /* If allowedStates.get(x) is null, == does not throw an NullPointerException */
      Boolean validStatusChange = allowedStates.get(current) == desired;

      if (!validStatusChange) {
        String firstOffender = entry.getKey().toString();
        p.fail(new ComposeException(403, URN_INVALID_INPUT.toString(),
            ERR_TITLE_CANT_CHANGE_APD_STATUS, firstOffender));
        return p.future();
      }
    }
    p.complete();
    return p.future();
  }

  @Override
  public ApdService createApd(CreateApdRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (!user.getRoles().contains(Roles.COS_ADMIN)) {
      Response r = new ResponseBuilder().status(401).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_COS_ADMIN_ROLE).detail(ERR_DETAIL_NO_COS_ADMIN_ROLE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String url = request.getUrl().toLowerCase();
    String name = request.getName();

    if (!InternetDomainName.isValid(url)) {
      Response resp = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_INVALID_DOMAIN)
          .detail(ERR_DETAIL_INVALID_DOMAIN).status(400).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    Tuple tuple = Tuple.of(name, url);
    /*
     * Disable APD existence check via /userclasses (apdWebClient.checkApdExists(url)) API for now.
     * TODO: maybe have a liveness check with a proper liveness API later on.
     */
    LOGGER.warn("APD liveness/existence check disabled!!!");
    Future<Boolean> isApdOnline = Future.succeededFuture(true);

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

    apdId.onSuccess(created -> {
      JsonObject response = new JsonObject();
      response.put(RESP_APD_ID, apdId.result().toString()).put(RESP_APD_NAME, name)
          .put(RESP_APD_URL, url).put(RESP_APD_STATUS, ApdStatus.ACTIVE.toString().toLowerCase());

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

  /**
   * can be called with either list of apdIds or list of apdUrls, not both at the same time get
   * details for all the elements in the req and the response has key value as req value and object
   * value contains details.
   */
  @Override
  public ApdService getApdDetails(
          List<String> apdUrl, List<String> apdIds, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String req;
    String query;
    Tuple tuple;
    List<String> request;
    Set<UUID> uniqueIds = new HashSet<UUID>();
    Set<String> uniqueUrl = new HashSet<>();
    // either apdUrl or apdId must be empty
    if (apdUrl.isEmpty() == apdIds.isEmpty()) {
      handler.handle(Future.failedFuture(INTERNALERROR));
      return this;
    }

    if (!apdIds.isEmpty()) {
      for (String ids : apdIds) {
        if (ids == null || !ids.matches(UUID_REGEX)) {
          handler.handle(
                  Future.failedFuture(
                          new ComposeException(
                                  400, URN_INVALID_INPUT, ERR_TITLE_INVALID_REQUEST, ERR_DETAIL_INVALID_UUID)));
          return this;
        }
        uniqueIds.add(UUID.fromString(ids));
      }
      req = "id";
      query = GET_APDINFO_ID;
      tuple = Tuple.of(uniqueIds.toArray(UUID[]::new));
      request = apdIds;
    } else {
      for (String ids : apdUrl)
        uniqueUrl.add(ids);
      req = "url";
      query = GET_APDINFO_URL;
      tuple = Tuple.of(uniqueUrl.toArray(String[]::new));
      request = apdUrl;
    }

    Collector<Row, ?, List<JsonObject>> ApdCollector =
            Collectors.mapping(row -> row.toJson(), Collectors.toList());
    Future<List<ApdInfoObj>> apdDetails =
        pool.withTransaction(
            conn ->
                conn.preparedQuery(query)
                    .collecting(ApdCollector)
                    .execute(tuple)
                    .map(res -> res.value())
                    .compose(
                        apdResp -> {
                          if (apdResp.isEmpty()) {
                            return Future.failedFuture(
                                new ComposeException(
                                    400,
                                    URN_INVALID_INPUT,
                                    ERR_TITLE_INVALID_REQUEST_ID,
                                    request.toString()));
                          }
                          List<String> apdIdList = new ArrayList<>();
                          if (req.equalsIgnoreCase("id")) {
                            apdIdList =
                                apdResp.stream()
                                    .map(obj -> obj.getString("id"))
                                    .collect(Collectors.toList());
                          } else {
                            apdIdList =
                                apdResp.stream()
                                    .map(obj -> obj.getString("url"))
                                    .collect(Collectors.toList());
                          }

                          if (!apdIdList.containsAll(request))
                          {
                            request.removeAll(apdIdList);
                            return Future.failedFuture(
                                new ComposeException(
                                    400,
                                    URN_INVALID_INPUT,
                                        ERR_TITLE_INVALID_REQUEST_ID,
                                    request.get(0).toString()));
                          }

                          List<ApdInfoObj> apdInfo = new ArrayList<>();
                          apdResp.forEach(
                              obj -> {
                                apdInfo.add(new ApdInfoObj(obj));
                              });
                          return Future.succeededFuture(apdInfo);
                        }));

    Future<JsonObject> responseFuture =
            apdDetails.compose(
                    res -> {
                      JsonObject response = new JsonObject();
                      List<ApdInfoObj> apdDetailList = apdDetails.result();
                      apdDetailList.forEach(
                              details -> {
                                JsonObject apdResponse = new JsonObject();
                                apdResponse.put("url", details.getUrl());
                                apdResponse.put("status", details.getStatus().toString().toLowerCase());
                                apdResponse.put("name", details.getName());
                                apdResponse.put("id",details.getId());
                                if (req.equalsIgnoreCase("id")) {
                                  response.put(details.getId(), apdResponse);
                                } else response.put(details.getUrl(), apdResponse);
                              });
                      return Future.succeededFuture(response);
                    });

    responseFuture
            .onSuccess(
                    response -> {
                      handler.handle(Future.succeededFuture(response));
                    })
            .onFailure(
                    e -> {
                      if (e instanceof ComposeException) {
                        ComposeException exp = (ComposeException) e;
                        handler.handle(Future.failedFuture(exp));
                        return;
                      }
                      LOGGER.error(e.getMessage());
                      handler.handle(Future.failedFuture(INTERNALERROR));
                    });
    return this;
  }

  /**
   * Calls RegistrationService.getUserDetails.
   * 
   * @param userIds List of strings of user IDs
   * @return a future of a Map, mapping the string user ID to a JSON object containing the user
   *         details
   */
  private Future<Map<String, JsonObject>> getUserDetails(List<String> userIds) {
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

  @Override
  public ApdService callApd(JsonObject apdContext, Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    
    /* TODO: make apdContext a class */
    String apdUrl = apdContext.getString("apdUrl");
    String userId = apdContext.getString("userId");
    String ownerId = apdContext.getString("ownerId");
    String itemId = apdContext.getString("itemId");
    String itemType = apdContext.getString("itemType");
    String rsUrl = apdContext.getString("resSerUrl");
    JsonObject context = apdContext.getJsonObject("context");

    Collector<Row, ?, List<JsonObject>> collector =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> apdDetails =
        pool.withConnection(conn -> conn.preparedQuery(SQL_GET_APD_URL_STATUS).collecting(collector)
            .execute(Tuple.of(apdUrl)).map(res -> res.value()));

    Future<Map<String, JsonObject>> userAndOwnerDetails =
        getUserDetails(List.of(userId, ownerId));

    Future<JsonObject> authAccessToken = apdDetails.compose(list -> {
      /* In case the APD URL in the CAT item was not registered on the server */
      if(list.isEmpty()) {
        return Future.failedFuture(new ComposeException(403, URN_INVALID_INPUT,
            ERR_TITLE_APD_NOT_REGISTERED, ERR_DETAIL_APD_NOT_REGISTERED));
      }

      Promise<JsonObject> promise = Promise.promise();
      tokenService.getAuthServerToken(apdUrl, promise);
      return promise.future();
    });

    Future<JsonObject> apdResponse =
        CompositeFuture.all(authAccessToken, userAndOwnerDetails).compose(res -> {
          JsonObject apdRequest = new JsonObject();

          JsonObject user = userAndOwnerDetails.result().get(userId);
          user.put("id", userId);
          JsonObject owner = userAndOwnerDetails.result().get(ownerId);
          owner.put("id", ownerId);
          JsonObject item = new JsonObject().put("itemId", itemId).put("itemType", itemType);

          String token = authAccessToken.result().getString("accessToken");

          apdRequest.put(APD_REQ_USER, user).put(APD_REQ_OWNER, owner).put(APD_REQ_ITEM, item)
              .put(APD_REQ_CONTEXT, context);

          return apdWebClient.callVerifyApdEndpoint(apdUrl, token, apdRequest);
        });

    /*
     * If the APD responds with an allow, a succeeded future is returned with a JSON object
     * containing the required information for the createToken service to create the access token. A
     * key `status` is set to `success` in the JSON object.
     * 
     * If the APD responds with a deny and needs interaction, then a succeeded future is returned
     * with a JSON object containing the required information for the createToken service to create
     * an APD token which the user will use to interact with the APD. The `status` key is set to
     * `apd-interaction`.
     * 
     * If the APD responds with a deny, a failed future is returned with a ComposeException
     * containing the error message sent by the APD in the `detail`.
     * 
     */
    apdResponse.onSuccess(response -> {
      JsonObject result = new JsonObject();

      if (response.getString(APD_RESP_TYPE).equals(APD_URN_ALLOW)) {
        
        // check if 'apdConstraints' are present
        if (response.containsKey(APD_CONSTRAINTS)) {
          result.put(CREATE_TOKEN_CONSTRAINTS, response.getJsonObject(APD_CONSTRAINTS));
        }

        result.put(CREATE_TOKEN_URL, rsUrl)
            .put(CREATE_TOKEN_CAT_ID, itemId).put(CREATE_TOKEN_STATUS, CREATE_TOKEN_SUCCESS);

        handler.handle(Future.succeededFuture(result));
        return;
      } else if (response.getString(APD_RESP_TYPE).equals(APD_URN_DENY_NEEDS_INT)) {
        /*
         * TODO: consider also passing the `detail` that the APD sends so that the createToken
         * service can use it in it's response
         */
        result.put(CREATE_TOKEN_URL, apdUrl)
            .put(CREATE_TOKEN_SESSIONID, response.getString(APD_RESP_SESSIONID))
            .put(CREATE_TOKEN_LINK, response.getString(APD_RESP_LINK, apdUrl))
            .put(CREATE_TOKEN_STATUS, CREATE_TOKEN_APD_INTERAC);

        handler.handle(Future.succeededFuture(result));
        return;
      }

      /* Add extra message if APD not active and has denied */
      String apdStatus = apdDetails.result().get(0).getString("status");
      String apdNotActiveMesg = APD_NOT_ACTIVE;
      if (ApdStatus.valueOf(apdStatus).equals(ApdStatus.ACTIVE)) {
        apdNotActiveMesg = "";
      }

      handler.handle(Future.failedFuture(
          new ComposeException(403, URN_INVALID_INPUT, ERR_TITLE_POLICY_EVAL_FAILED,
              response.getString(APD_RESP_DETAIL) + apdNotActiveMesg)));
    }).onFailure(e -> {
      if (e instanceof ComposeException) {
        /* Add extra message if APD not active and has denied */
        String apdStatus = apdDetails.result().get(0).getString("status");
        String apdNotActiveMesg = APD_NOT_ACTIVE;
        if (ApdStatus.valueOf(apdStatus).equals(ApdStatus.ACTIVE)) {
          apdNotActiveMesg = "";
        }

        ComposeException exp = (ComposeException) e;
        Response err = exp.getResponse();
        err.setDetail(err.getDetail() + apdNotActiveMesg);
        err.setTitle(ERR_TITLE_POLICY_EVAL_FAILED);
        /* send back failed future - returning to createToken and not API server */
        handler.handle(Future.failedFuture(new ComposeException(err)));
        return;
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });

    return this;
  }
}
