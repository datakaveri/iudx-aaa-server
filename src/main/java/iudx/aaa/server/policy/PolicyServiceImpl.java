package iudx.aaa.server.policy;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.*;
import iudx.aaa.server.registration.RegistrationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.*;

/**
 * The Policy Service Implementation.
 *
 * <h1>Policy Service Implementation</h1>
 *
 * <p>The Policy Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.policy.PolicyService}.
 *
 * @version 1.0
 * @since 2020-12-15
 */
public class PolicyServiceImpl implements PolicyService {

  private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);

  private final PgPool pool;
  private final RegistrationService registrationService;
  private final deletePolicy deletePolicy;
  private final createPolicy createPolicy;
  private final CatalogueClient catalogueClient;

  // Create the pooled client

  public PolicyServiceImpl(
      PgPool pool, RegistrationService registrationService, CatalogueClient catalogueClient) {
    this.pool = pool;
    this.registrationService = registrationService;
    this.catalogueClient = catalogueClient;
    this.deletePolicy = new deletePolicy(pool);
    this.createPolicy = new createPolicy(pool);
  }

  @Override
  public PolicyService createPolicy(
      List<CreatePolicyRequest> request, User user, Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    List<Roles> roles = user.getRoles();

    if (!roles.contains(Roles.ADMIN)
        && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE)) {
      // 403 not allowed to create policy
      Response r =
          new Response.ResponseBuilder()
              .type(INVALID_ROLE)
              .title(URN_INVALID_ROLE)
              .detail(INVALID_ROLE)
              .status(403)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Set<UUID> users =
        request.stream().map(e -> UUID.fromString(e.getUserId())).collect(Collectors.toSet());

    Future<Set<UUID>> UserExist = createPolicy.checkUserExist(users);

    List<String> exp =
        request.stream()
            .filter(tagObject -> !tagObject.getExpiryTime().isEmpty())
            .map(CreatePolicyRequest::getExpiryTime)
            .collect(Collectors.toList());

    Future<Void> validateExp = createPolicy.validateExpiry(exp);

    List<String> resServerIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_SERVER.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    List<String> resGrpIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_GROUP.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    List<String> resIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    Map<String, List<String>> catItem = new HashMap<>();

    if (resServerIds.size() > 0) catItem.put(RES_SERVER, resServerIds);
    else {
      if (resGrpIds.size() > 0) catItem.put(RES_GRP, resGrpIds);
      if (resIds.size() > 0) catItem.put(RES, resIds);
    }

    Future<Map<String, UUID>> reqItemDetail =
        catalogueClient.checkReqItems(catItem, user.getUserId());

    Future<Boolean> ItemChecks =
        CompositeFuture.all(UserExist, validateExp, reqItemDetail)
            .compose(
                obj -> {
                  if (!users.equals(UserExist.result())) {
                    LOGGER.debug("UserExist fail:: " + UserExist.result().toString());
                    return Future.failedFuture(INVALID_USER + UserExist.result());
                  }

                  if (catItem.containsKey(RES_SERVER)) return Future.succeededFuture(false);
                  return Future.succeededFuture(true);
                });

    Future<Boolean> checkAuthPolicy =
        ItemChecks.compose(
            obj -> {
              if (ItemChecks.result().equals(false)) return Future.succeededFuture(false);
              return catalogueClient.checkAuthPolicy(user.getUserId());
            });

    Future<List<UUID>> checkOwner =
        checkAuthPolicy.compose(
            succ -> {
              if (succ.equals(false)) return Future.succeededFuture(new ArrayList<>());
              return catalogueClient.checkOwner(catItem, user.getUserId());
            });

    Future<List<UUID>> checkDelegate =
        checkOwner.compose(
            checkOwn -> {
              if (checkOwn.isEmpty()) return Future.succeededFuture(new ArrayList<>());
              return catalogueClient.checkDelegate(checkOwn, user.getUserId());
            });

    Future<Map<String, UUID>> getOwner =
        checkDelegate.compose(
            checkDel -> {
              if (checkDel.size() < checkOwner.result().size())
                return Future.failedFuture(UNAUTHORIZED);

              if (catItem.containsKey(RES_SERVER)) return Future.succeededFuture(new HashMap<>());

              return catalogueClient.getOwnerId(catItem);
            });

    Future<Boolean> insertPolicy =
        getOwner.compose(
            succ -> {
              return createPolicy.insertPolicy(
                  request, reqItemDetail.result(), getOwner.result(), user);
            });

    insertPolicy
        .onSuccess(
            succ -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(POLICY_SUCCESS)
                      .title("added policies")
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            obj -> {
              obj.printStackTrace();
              Response r = createPolicy.getRespObj(obj.getLocalizedMessage());
              handler.handle(Future.succeededFuture(r.toJson()));
            });
    return this;
  }

  @Override
  public PolicyService deletePolicy(
      JsonArray request, User user, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    // check if all req items exist to delete;

    List<UUID> req =
        request.stream()
            .map(JsonObject.class::cast)
            .filter(tagObject -> !tagObject.getString(ID).isEmpty())
            .map(tagObject -> UUID.fromString(tagObject.getString(ID)))
            .collect(Collectors.toList());

    deletePolicy
        .checkResExist(req)
        // if failed, End req
        .onFailure(
            // internal server error
            failureHandler -> {
              LOGGER.error("failed checkResExist: " + failureHandler.getLocalizedMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            })
        .onSuccess(
            checkSuc -> {
              if (checkSuc.size() > 0)
              // checkResExist returns items that do not exist
              {
                Response r =
                    new Response.ResponseBuilder()
                        .type(POLICY_FAILURE)
                        .title(ID_NOT_PRESENT)
                        .detail(ID_NOT_PRESENT + checkSuc.toString())
                        .status(400)
                        .build();
                handler.handle(Future.succeededFuture(r.toJson()));
              } else {
                deletePolicy
                    .CheckResOwner(req, user.getUserId())
                    .onFailure(
                        failureHandler -> {
                          LOGGER.error(
                              "failed CheckResOwner: " + failureHandler.getLocalizedMessage());
                          handler.handle(Future.failedFuture(INTERNALERROR));
                        })
                    .onSuccess(
                        ar -> {
                          if (ar.size() > 0) {
                            // if false then check for delegate role
                            if (user.getRoles().contains(Roles.DELEGATE)) {
                              // check if delegate is delegate for all policies
                              deletePolicy
                                  .checkDelegatePolicy(user.getUserId(), req)
                                  .onFailure(
                                      // internal server error
                                      failureHandler -> {
                                        LOGGER.error(
                                            "failed checkDelegatePolicy: "
                                                + failureHandler.getLocalizedMessage());
                                        handler.handle(Future.failedFuture(INTERNALERROR));
                                      })
                                  .onSuccess(
                                      succ -> {
                                        if (!succ) { // 403
                                          Response r =
                                              new Response.ResponseBuilder()
                                                  .type(POLICY_FAILURE)
                                                  .title(INVALID_DELEGATE_POL)
                                                  .detail(AUTH_DEL_FAIL)
                                                  .status(403)
                                                  .build();
                                          handler.handle(Future.succeededFuture(r.toJson()));
                                        } else { // check delegate
                                          deletePolicy
                                              .checkDelegate(user.getUserId(), req)
                                              .onFailure(
                                                  // internal server error
                                                  failureHandler -> {
                                                    LOGGER.error(
                                                        "failed checkDelegate: "
                                                            + failureHandler.getLocalizedMessage());
                                                    handler.handle(
                                                        Future.failedFuture(INTERNALERROR));
                                                  })
                                              .onSuccess(
                                                  success -> {
                                                    if (success.size() <= 0) {
                                                      deletePolicy
                                                          .delPolicy(req)
                                                          .onFailure(
                                                              // internal server error
                                                              failureHandler -> {
                                                                LOGGER.error(
                                                                    "failed checkDelegate: "
                                                                        + failureHandler
                                                                            .getLocalizedMessage());
                                                                handler.handle(
                                                                    Future.failedFuture(
                                                                        INTERNALERROR));
                                                              })
                                                          .onSuccess(
                                                              resp -> {
                                                                Response r =
                                                                    new Response.ResponseBuilder()
                                                                        .type(POLICY_SUCCESS)
                                                                        .title(
                                                                            SUCC_TITLE_POLICY_DEL)
                                                                        .status(200)
                                                                        .build();
                                                                handler.handle(
                                                                    Future.succeededFuture(
                                                                        r.toJson()));
                                                              });
                                                    } else {
                                                      Response r =
                                                          new Response.ResponseBuilder()
                                                              .type(URN_INVALID_DELEGATE)
                                                              .title(INVALID_DELEGATE)
                                                              .detail(
                                                                  "invalid role for "
                                                                      + success.toString())
                                                              .status(403)
                                                              .build();
                                                      handler.handle(
                                                          Future.succeededFuture(r.toJson()));
                                                    }
                                                  });
                                        }
                                      });
                            }
                            // if not delegate, then 403
                            else {
                              Response r =
                                  new Response.ResponseBuilder()
                                      .type(URN_INVALID_ROLE)
                                      .title(INVALID_ROLE)
                                      .detail("invalid role for " + ar.toString())
                                      .status(403)
                                      .build();
                              handler.handle(Future.succeededFuture(r.toJson()));
                            }
                          } else {
                            deletePolicy
                                .delPolicy(req)
                                .onFailure(
                                    failure -> {
                                      Response r =
                                          new Response.ResponseBuilder()
                                              .type(POLICY_FAILURE)
                                              .title(DELETE_FAILURE)
                                              .status(500)
                                              .build();
                                      handler.handle(Future.succeededFuture(r.toJson()));
                                    })
                                .onSuccess(
                                    succ -> {
                                      Response r =
                                          new Response.ResponseBuilder()
                                              .type(POLICY_SUCCESS)
                                              .title(SUCC_TITLE_POLICY_DEL)
                                              .status(200)
                                              .build();
                                      handler.handle(Future.succeededFuture(r.toJson()));
                                    });
                          }
                        });
              }
            });

    return this;
  }

  @Override
  public PolicyService listPolicy(User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    JsonArray respPolicies = new JsonArray();
    JsonObject response = new JsonObject();

    UUID user_id;
     if(data.containsKey(USER_ID))
         user_id = UUID.fromString(data.getString(USER_ID));
    else
         user_id = UUID.fromString(user.getUserId());

    Collector<Row, ?, List<JsonObject>> policyCollector =
        Collectors.mapping(Row::toJson, Collectors.toList());

    Future<List<JsonObject>> getResGrpPolicy =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(GET_POLICIES + itemTypes.RESOURCE_GROUP + GET_POLICIES_JOIN)
                        .collecting(policyCollector)
                        .execute(Tuple.of(user_id, itemTypes.RESOURCE_GROUP, status.ACTIVE))
                        .map(SqlResult::value))
            .onFailure(
                obj -> {
                  LOGGER.error("failed getResGrpPolicy  " + obj.getMessage());
                  handler.handle(Future.failedFuture(INTERNALERROR));
                });

    Future<List<JsonObject>> getResIdPolicy =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(GET_POLICIES + itemTypes.RESOURCE + GET_POLICIES_JOIN)
                        .collecting(policyCollector)
                        .execute(Tuple.of(user_id, itemTypes.RESOURCE, status.ACTIVE))
                        .map(SqlResult::value))
            .onFailure(
                obj -> {
                  LOGGER.error("failed getResIdPolicy  " + obj.getMessage());
                  handler.handle(Future.failedFuture(INTERNALERROR));
                });

    Future<List<JsonObject>> getGrpPolicy =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(
                            GET_POLICIES + itemTypes.RESOURCE_GROUP + GET_POLICIES_JOIN_OWNER)
                        .collecting(policyCollector)
                        .execute(Tuple.of(user_id, itemTypes.RESOURCE_GROUP, status.ACTIVE))
                        .map(SqlResult::value))
            .onFailure(
                obj -> {
                  LOGGER.error(obj.getMessage());
                  handler.handle(Future.failedFuture(INTERNALERROR));
                });

    Future<List<JsonObject>> getIdPolicy = Future.succeededFuture();
    pool.withConnection(
            conn ->
                conn.preparedQuery(GET_POLICIES + itemTypes.RESOURCE + GET_POLICIES_JOIN_OWNER)
                    .collecting(policyCollector)
                    .execute(Tuple.of(user_id, itemTypes.RESOURCE, status.ACTIVE))
                    .map(SqlResult::value))
        .onFailure(
            obj -> {
              LOGGER.error(obj.getMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            });

    CompositeFuture.all(getResGrpPolicy, getResIdPolicy, getGrpPolicy, getIdPolicy)
        .onSuccess(
            obj -> {
              List<JsonObject> policies = new ArrayList<>();

              if (obj.list().get(0) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(0));
              }

              if (obj.list().get(1) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(1));
              }

              if (obj.list().get(2) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(2));
              }

              if (obj.list().get(3) != null) {
                policies.addAll((List<JsonObject>) obj.list().get(3));
              }

              policies = new ArrayList<>(new HashSet<>(policies));

              List<String> userId =
                  policies.stream()
                      .map(JsonObject.class::cast)
                      .filter(tagObject -> !tagObject.getString(USER_ID).isEmpty())
                      .map(tagObject -> tagObject.getString(USER_ID))
                      .collect(Collectors.toList());

              userId.addAll(
                  policies.stream()
                      .map(JsonObject.class::cast)
                      .filter(tagObject -> !tagObject.getString(OWNER_ID).isEmpty())
                      .map(tagObject -> tagObject.getString(OWNER_ID))
                      .collect(Collectors.toList()));

              if (userId != null) {
                userId = new ArrayList<>(new HashSet<>(userId));

                List<JsonObject> finalPolicies = policies;
                registrationService.getUserDetails(
                    userId,
                    res -> {
                      if (res.succeeded()) {
                        String uid;
                        String oid;
                        String itemType;
                        JsonObject object;
                        for (JsonObject ar : finalPolicies) {
                          uid = ar.getString(USER_ID);
                          oid = ar.getString(OWNER_ID);
                          itemType = ar.getString("item_type");
                          ar.put("item_type", itemType.toLowerCase());
                          if (res.result().containsKey(uid)) {
                            ar.remove(USER_ID);
                            object = res.result().get(uid);
                            object.put("id", uid);
                            ar.put(USER_DETAILS, object);
                            respPolicies.add(ar);
                          }

                          if (res.result().containsKey(oid)) {
                            ar.remove(OWNER_ID);
                            object = res.result().get(oid);
                            object.put("id", oid);
                            ar.put(OWNER_DETAILS, object);
                            respPolicies.add(ar);
                          }
                        }
                        response.put("result", respPolicies);
                        Response r =
                            new Response.ResponseBuilder()
                                .type(POLICY_SUCCESS)
                                .title(SUCC_TITLE_POLICY_READ)
                                .status(200)
                                .objectResults(response)
                                .build();
                        handler.handle(Future.succeededFuture(r.toJson()));
                      } else if (res.failed()) {
                        LOGGER.error("Registration failure :" + res.cause());
                        handler.handle(Future.failedFuture(INTERNALERROR));
                      }
                    });
              } else {
                Response r =
                    new Response.ResponseBuilder()
                        .type(POLICY_SUCCESS)
                        .title(SUCC_TITLE_POLICY_READ)
                        .status(200)
                        .detail("no policies")
                        .build();
                handler.handle(Future.succeededFuture(r.toJson()));
              }
            })
        .onFailure(
            obj -> {
              LOGGER.error(obj.getMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            });

    return this;
  }

  Future<JsonObject> verifyConsumerPolicy(
      UUID userId, String itemId, String itemType, String role, JsonObject res) {
    Promise<JsonObject> p = Promise.promise();

    Future<String> getConstraints =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(GET_CONSUMER_CONSTRAINTS)
                        .execute(
                            Tuple.of(
                                userId,
                                res.getString(ITEM_ID),
                                itemType.toUpperCase(),
                                status.ACTIVE)))
            .compose(
                ar -> {
                  if (ar.toString().isEmpty()) return Future.failedFuture("policy does not exist");
                  else {
                    return Future.succeededFuture(ar.toString());
                  }
                });

    Future<String> getUrl =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(GET_URL)
                        .execute(Tuple.of(res.getString(RESOURCE_SERVER_ID))))
            .compose(
                ar -> {
                  if (ar.rowCount() <= 0) return Future.failedFuture(NO_RES_SERVER);
                  else {
                    return Future.succeededFuture(ar.value().toString());
                  }
                });

    CompositeFuture.all(getConstraints, getUrl)
        .onSuccess(
            success -> {
              if (!getConstraints.result().isEmpty() && !getUrl.result().isEmpty()) {
                JsonObject details = new JsonObject();
                details.put(CONSTRAINTS, getConstraints.result());
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getUrl.result());
                p.complete(details);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("failed verifyConsumerPolicy: " + failureHandler.getLocalizedMessage());
              p.fail(failureHandler.getLocalizedMessage());
            });

    return p.future();
  }

  Future<JsonObject> verifyProviderPolicy(
      UUID userId, String itemId, String itemType, JsonObject res, boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

    String email_hash = itemId.split("/")[0] + "/" + itemId.split("/")[1];

    Future<UUID> getResOwner =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_RES_OWNER)
                    .execute(Tuple.of(email_hash))
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));

    Future<JsonObject> getResSerOwner;
    if (isCatalogue) {
      getResSerOwner =
          getResOwner.compose(
              ar -> {
                if (ar == null) return Future.failedFuture(NO_USER);
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(CAT_SERVER_URL))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getResOwner.compose(
              ar -> {
                if (!getResOwner.result().equals(userId)) return Future.failedFuture(NOT_RES_OWNER);
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SER_OWNER + itemType + GET_RES_SER_OWNER_JOIN)
                            .execute(Tuple.of(itemId))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    }

    Future<UUID> checkAdminPolicy =
        getResSerOwner.compose(
            success ->
                pool.withConnection(
                        conn -> {
                          if (isCatalogue) res.put(PROVIDER_ID, success.getString(OWNER_ID));
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      res.getString(PROVIDER_ID),
                                      success.getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) return Future.failedFuture(NO_ADMIN_POLICY);
                          return Future.succeededFuture(obj);
                        })
                    .onFailure(
                        failureHandler ->
                            Future.failedFuture(
                                "checkAdminPolicy db fail ::"
                                    + failureHandler.getLocalizedMessage())));

    checkAdminPolicy
        .onSuccess(
            success -> {
              if (!success.toString().isEmpty()) {
                JsonObject details = new JsonObject();
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getResSerOwner.result().getString("url"));
                p.complete(details);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("failed verifyProviderPolicy: " + failureHandler.getLocalizedMessage());
              p.fail(failureHandler.getLocalizedMessage());
            });

    return p.future();
  }

  Future<JsonObject> verifyDelegatePolicy(
      UUID userId, String itemId, String itemType, JsonObject res, boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

    String email_hash = itemId.split("/")[0] + "/" + itemId.split("/")[1];
    Future<UUID> getOwner;
    if (isCatalogue)
      getOwner =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_RES_OWNER)
                      .execute(Tuple.of(email_hash))
                      .map(
                          rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));
    else getOwner = Future.succeededFuture(UUID.fromString(res.getString(PROVIDER_ID)));

    Future<JsonObject> getResSerOwner;
    if (isCatalogue) {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) return Future.failedFuture(NO_USER);
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(CAT_SERVER_URL))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) return Future.failedFuture(NO_RES_SERVER);
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SER_OWNER + itemType + GET_RES_SER_OWNER_JOIN)
                            .execute(Tuple.of(itemId))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    }

    Future<UUID> checkDelegation =
        getResSerOwner.compose(
            ar -> {
              if (getOwner.result() == null) return Future.failedFuture(UNAUTHORIZED_DELEGATE);
              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(CHECK_DELEGATOINS_VERIFY)
                          .execute(
                              Tuple.of(
                                  userId,
                                  getOwner.result(),
                                  getResSerOwner.result().getString(ID),
                                  status.ACTIVE.toString()))
                          .map(
                              rows ->
                                  rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));
            });

    Future<JsonObject> checkPolicy;
    if (isCatalogue)
      checkPolicy = checkDelegation.compose(obj -> Future.succeededFuture(new JsonObject()));
    else
      checkPolicy =
          checkDelegation.compose(
              ar ->
                  pool.withConnection(
                      conn ->
                          conn.preparedQuery(CHECK_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      getOwner.result(),
                                      res.getString("id"),
                                      status.ACTIVE.toString()))
                              .map(
                                  rows ->
                                      rows.rowCount() > 0
                                          ? rows.iterator().next().toJson()
                                          : null)));

    Future<UUID> checkAdminPolicy =
        checkPolicy.compose(
            success ->
                pool.withConnection(
                        conn -> {
                          if (checkPolicy.result() == null)
                            return Future.failedFuture(UNAUTHORIZED_DELEGATE);
                          if (isCatalogue)
                            res.put("provider_id", getResSerOwner.result().getString("owner_id"));
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      res.getString(PROVIDER_ID),
                                      getResSerOwner.result().getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) return Future.failedFuture(NO_ADMIN_POLICY);
                          return Future.succeededFuture(obj);
                        })
                    .onFailure(
                        failureHandler ->
                            Future.failedFuture(
                                "checkAdminPolicy db fail ::"
                                    + failureHandler.getLocalizedMessage())));

    checkAdminPolicy
        .onSuccess(
            success -> {
              if (!success.toString().isEmpty()) {
                JsonObject details = new JsonObject();
                details.put(STATUS, SUCCESS);
                details.put(CAT_ID, itemId);
                details.put(URL, getResSerOwner.result().getString(URL));
                p.complete(details);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("failed verifyDelegatePolicy: " + failureHandler.getLocalizedMessage());
              p.fail(failureHandler.getLocalizedMessage());
            });

    return p.future();
  }

  @Override
  public PolicyService verifyPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    UUID userId = UUID.fromString(request.getString(USERID));
    String itemId = request.getString(ITEMID);
    String itemType = request.getString(ITEMTYPE).toUpperCase();
    String role = request.getString(ROLE).toUpperCase();
    boolean isCatalogue = false;

    Future<String> getRoles =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_FROM_ROLES_TABLE)
                    .execute(Tuple.of(userId, roles.valueOf(role), status.APPROVED))
                    .compose(
                        ar -> {
                          if (ar.rowCount() > 0) {
                            return Future.succeededFuture(ar.iterator().next().getString(ROLE));
                          }
                          return Future.failedFuture(ROLE_NOT_FOUND);
                        }));

    if (itemId.split("/").length == 5
        && itemId.split("/")[2].equals(CAT_SERVER_URL)
        && (itemId.split("/")[3] + "/" + itemId.split("/")[4]).equals(CAT_ITEM)) {
      isCatalogue = true;
    }

    Future<JsonObject> getResDetail;
    if (!isCatalogue) {
      getResDetail =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_RES_DETAIL + itemType.toLowerCase() + GET_RES_DETAIL_JOIN)
                      .execute(Tuple.of(itemId))
                      .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null)
                      .onFailure(failHandler -> Future.failedFuture("getResDetail fail"))
                      .compose(
                          ar -> {
                            if (ar.size() <= 0) return Future.failedFuture(ITEMNOTFOUND);
                            else return Future.succeededFuture(ar);
                          }));
    } else getResDetail = Future.succeededFuture(new JsonObject());

    boolean finalIsCatalogue = isCatalogue;
    Future<JsonObject> verifyRolePolicy =
        CompositeFuture.all(getRoles, getResDetail)
            .compose(
                success -> {
                  Future<JsonObject> response;
                  switch (getRoles.result()) {
                    case CONSUMER_ROLE:
                      {
                        response =
                            verifyConsumerPolicy(
                                userId, itemId, itemType, role, getResDetail.result());

                        break;
                      }
                    case PROVIDER_ROLE:
                      {
                        response =
                            verifyProviderPolicy(
                                userId, itemId, itemType, getResDetail.result(), finalIsCatalogue);
                        break;
                      }
                    case DELEGATE_ROLE:
                      {
                        response =
                            verifyDelegatePolicy(
                                userId, itemId, itemType, getResDetail.result(), finalIsCatalogue);
                        break;
                      }
                    default:
                      {
                        response = Future.failedFuture(INTERNALERROR);
                      }
                  }
                  return response;
                })
            .onFailure(
                f -> {
                  Future.failedFuture(f.getLocalizedMessage());
                });

    verifyRolePolicy.onSuccess(
        s -> {
          handler.handle(Future.succeededFuture(s));
        });

    verifyRolePolicy.onFailure(
        f -> {
          handler.handle(Future.failedFuture(f.getLocalizedMessage()));
        });

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService setDefaultProviderPolicies(
      List<String> userIds, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    handler.handle(Future.succeededFuture(new JsonObject()));
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService createPolicyNotification(
      List<CreatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler) {

    handler.handle(Future.succeededFuture(new JsonObject()));
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService listPolicyNotification(User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
    
    handler.handle(Future.succeededFuture(data));

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService updatelistPolicyNotification(
      List<UpdatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler) {

    handler.handle(Future.succeededFuture(new JsonObject()));
    return this;
  }
}
