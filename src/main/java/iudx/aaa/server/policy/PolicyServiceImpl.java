package iudx.aaa.server.policy;

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

import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.DeletePolicyRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.policy.Constants.itemTypes;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdatePolicyNotification;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.registration.RegistrationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.token.Constants.INVALID_RS_URL;
import static iudx.aaa.server.token.Constants.URN_INVALID_INPUT;

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
    
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    
    List<Roles> roles = user.getRoles();

    if (!roles.contains(Roles.DELEGATE) && !roles.contains(Roles.CONSUMER)) {
      Response r = new Response.ResponseBuilder().type(INVALID_ROLE).title(URN_INVALID_ROLE)
          .detail(INVALID_ROLE).status(403).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resServerIds = request.stream()
        .filter(tagObject -> tagObject.getItemType().toUpperCase()
            .equals(itemTypes.RESOURCE_SERVER.toString()))
        .map(CreatePolicyNotification::getItemId)
        .collect(Collectors.toList());

    List<String> resGrpIds = request.stream()
        .filter(tagObject -> tagObject.getItemType().toUpperCase()
            .equals(itemTypes.RESOURCE_GROUP.toString()))
        .map(CreatePolicyNotification::getItemId)
        .collect(Collectors.toList());
    

    List<String> resIds = request.stream()
        .filter(tagObject -> tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
        .map(CreatePolicyNotification::getItemId)
        .collect(Collectors.toList());

    Map<String, List<String>> catItem = new HashMap<>();

    if (resServerIds.size() > 0) {
      catItem.put(RES_SERVER, resServerIds);
    } else {
      if (resGrpIds.size() > 0) {
        catItem.put(RES_GRP, resGrpIds);
      }
      if (resIds.size() > 0) {
        catItem.put(RES, resIds);
      }
    }
    
    Future<Map<String, UUID>> reqCatItem =
        catalogueClient.checkReqItems(catItem, user.getUserId());
    Future<Map<String, UUID>> ownerId = catalogueClient.getOwnerId(catItem);
    
    CompositeFuture.all(reqCatItem, ownerId).onComplete(dbHandler -> {
      
      if (dbHandler.failed()) {
        LOGGER.error("Fail: " + NO_RES_SERVER);
        Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(NO_RES_SERVER).detail(NO_RES_SERVER).build();
        handler.handle(Future.succeededFuture(resp.toJson()));
        return;
      }

      if (dbHandler.succeeded()) {
        Future<List<Tuple>> tuples = mapTuple(request, reqCatItem.result(), ownerId.result(), user);
        tuples.onSuccess(resHandler -> {
          pool.withTransaction(conn -> conn.preparedQuery(CREATE_NOTIFI_POLICY_REQUEST)
              .executeBatch(resHandler).onFailure(insertHandler -> {
                System.out.println(insertHandler);
              }));
        }).onFailure(failureHandler -> {
          LOGGER.error(LOG_DB_ERROR + failureHandler.getLocalizedMessage());
          Response resp = new ResponseBuilder().status(500).type(URN_INVALID_INPUT)
              .title(INTERNALERROR).detail(INTERNALERROR).build();
          handler.handle(Future.succeededFuture(resp.toJson()));
          return;
        });
      }
    });
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

  @Override
  public PolicyService listDelegation(User user, JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER)
        || user.getRoles().contains(Roles.DELEGATE))) {
      Response r =
          new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES).status(401).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String query;
    Tuple queryTup;

    /* get all delegations EXCEPT auth server delegations */
    if (isAuthDelegate) {
      UUID providerUserId = UUID.fromString(authDelegateDetails.getString("providerId"));
      query = LIST_DELEGATE_AUTH_DELEGATE;
      queryTup = Tuple.of(providerUserId, AUTH_SERVER_URL);
    } else {
      query = LIST_DELEGATE_AS_PROVIDER_DELEGATE;
      queryTup = Tuple.of(UUID.fromString(user.getUserId()));
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> data = pool.withConnection(conn -> conn.preparedQuery(query)
        .collecting(collect).execute(queryTup).map(res -> res.value()));

    Future<Map<String, JsonObject>> userInfo = data.compose(result -> {
      Set<String> ss = new HashSet<String>();
      result.forEach(obj -> {
        ss.add(obj.getString("owner_id"));
        ss.add(obj.getString("user_id"));
      });

      Promise<Map<String, JsonObject>> userDetails = Promise.promise();
      registrationService.getUserDetails(new ArrayList<String>(ss), userDetails);
      return userDetails.future();
    });

    userInfo.onSuccess(details -> {
      List<JsonObject> deleRes = data.result();

      deleRes.forEach(obj -> {
        JsonObject ownerDet = details.get(obj.getString("owner_id"));
        ownerDet.put("id", obj.remove("owner_id"));

        JsonObject userDet = details.get(obj.getString("user_id"));
        userDet.put("id", obj.remove("user_id"));

        obj.put("owner", ownerDet);
        obj.put("user", userDet);
      });
      Response r = new ResponseBuilder().type(POLICY_SUCCESS).title(SUCC_TITLE_LIST_DELEGS)
          .arrayResults(new JsonArray(deleRes)).status(200).build();
      handler.handle(Future.succeededFuture(r.toJson()));
    }).onFailure(e -> {
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });
    return this;
  }

  @Override
  public PolicyService deleteDelegation(List<DeleteDelegationRequest> request, User user,
      JsonObject authDelegateDetails, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER))) {
        Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE)
            .title(ERR_TITLE_INVALID_ROLES).detail(ERR_DETAIL_DEL_DELEGATE_ROLES).status(401).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
    }

    Tuple queryTup;
    List<UUID> ids =
        request.stream().map(obj -> UUID.fromString(obj.getId())).collect(Collectors.toList());

    if (isAuthDelegate) {
      UUID providerUserId = UUID.fromString(authDelegateDetails.getString("providerId"));
      queryTup = Tuple.of(providerUserId).addArrayOfUUID(ids.toArray(UUID[]::new));
    } else {
      queryTup =
          Tuple.of(UUID.fromString(user.getUserId())).addArrayOfUUID(ids.toArray(UUID[]::new));
    }

    Collector<Row, ?, Map<UUID, String>> collect =
        Collectors.toMap(row -> row.getUUID("id"), row -> row.getString("url"));

    Future<Map<UUID, String>> idServerMap =
        pool.withConnection(conn -> conn.preparedQuery(GET_DELEGATIONS_BY_ID).collecting(collect)
            .execute(queryTup).map(res -> res.value()));

    Future<Void> validate = idServerMap.compose(data -> {
      if (data.size() != ids.size()) {
        List<UUID> badIds =
            ids.stream().filter(id -> !data.containsKey(id)).collect(Collectors.toList());

        Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT)
            .title(ERR_TITLE_INVALID_ID).detail(badIds.get(0).toString()).status(400).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }

      if (!isAuthDelegate) {
        return Future.succeededFuture();
      }

      List<UUID> authDelegs =
          data.entrySet().stream().filter(obj -> obj.getValue().equals(AUTH_SERVER_URL))
              .map(obj -> obj.getKey()).collect(Collectors.toList());
      
      if (!authDelegs.isEmpty()) {
        Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT)
            .title(ERR_TITLE_AUTH_DELE_DELETE).detail(authDelegs.get(0).toString()).status(403)
            .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }
      return Future.succeededFuture();
    });

    validate.compose(
        i -> pool.withTransaction(conn -> conn.preparedQuery(DELETE_DELEGATIONS).execute(queryTup)))
        .onSuccess(res -> {
          Response r = new Response.ResponseBuilder().type(POLICY_SUCCESS)
              .title(SUCC_TITLE_DELETE_DELE).objectResults(new JsonObject()).status(200).build();
          handler.handle(Future.succeededFuture(r.toJson()));
        }).onFailure(e -> {
          if (e.getMessage().equals(COMPOSE_FAILURE)) {
            return; // do nothing
          }
          LOGGER.error(e.getMessage());
          handler.handle(Future.failedFuture("Internal error"));
        });

    return this;
  }
  
  /**
   * Create/map objects, fields to List<tuple>.
   * 
   * @param request
   * @param resourceId
   * @param ownerId
   * @param user
   * @return List<Tuple>
   */
  Future<List<Tuple>> mapTuple(List<CreatePolicyNotification> request, Map<String, UUID> resourceId,
      Map<String, UUID> ownerIds, User user) {

    Promise<List<Tuple>> promise = Promise.promise();
    List<Tuple> tuples = new ArrayList<>();

    String userId = user.getUserId();
    try {
      for (CreatePolicyNotification each : request) {

        String catId = each.getItemId();
        UUID itemId = resourceId.get(catId);
        UUID ownerId = ownerIds.get(catId);

        String status = RoleStatus.PENDING.name();
        String itemType = each.getItemType().toUpperCase();
        
        Duration duration = DatatypeFactory.newInstance().newDuration(each.getExpiryDuration());
        JsonObject constraints = each.getConstraints();

        tuples.add(Tuple.of(userId, itemId, itemType, ownerId, status, each.getExpiryDuration(), constraints));
      }
    } catch (DatatypeConfigurationException e) {
      promise.fail(e.getLocalizedMessage());
    }

    promise.complete(tuples);
    return promise.future();
  }

}
