package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CALL_APD_APDURL;
import static iudx.aaa.server.policy.Constants.CALL_APD_CONTEXT;
import static iudx.aaa.server.policy.Constants.CALL_APD_ITEM_ID;
import static iudx.aaa.server.policy.Constants.CALL_APD_ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.CALL_APD_OWNERID;
import static iudx.aaa.server.policy.Constants.CALL_APD_RES_SER_URL;
import static iudx.aaa.server.policy.Constants.CALL_APD_USERID;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_DID;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_DRL;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_RG;
import static iudx.aaa.server.policy.Constants.DELETE_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.ERR_CONTEXT_EXISTING_DELEGATION_IDS;
import static iudx.aaa.server.policy.Constants.ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_CREATE_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DEL_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_LIST_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ID;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
import static iudx.aaa.server.policy.Constants.GET_DELEGATIONS_BY_ID;
import static iudx.aaa.server.policy.Constants.GET_ROLE_IDS_BY_ROLE_AND_RS;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_ID;
import static iudx.aaa.server.policy.Constants.INSERT_DELEGATION;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.LIST_DELEGATION_AS_DELEGATOR_OR_DELEGATE;
import static iudx.aaa.server.policy.Constants.NOT_RES_OWNER;
import static iudx.aaa.server.policy.Constants.RESP_DELEG_EMAILS;
import static iudx.aaa.server.policy.Constants.SQL_GET_DELEG_USER_IDS_BY_DELEGATION_INFO;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCCESS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELEG_EMAILS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELETE_DELE;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_LIST_DELEGS;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.UUID_REGEX;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.token.Constants.ACCESS_DENIED;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.DelegationStatus;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.registration.RegistrationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  private final ApdService apdService;
  private final CatalogueClient catalogueClient;

  // Create the pooled client
  /* for converting getUserDetails's JsonObject to map */
  Function<JsonObject, Map<String, JsonObject>> jsonObjectToMap =
      (obj) -> {
        return obj.stream()
            .collect(
                Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
      };

  public PolicyServiceImpl(
      PgPool pool,
      RegistrationService registrationService,
      ApdService apdService,
      CatalogueClient catalogueClient) {
    this.pool = pool;
    this.registrationService = registrationService;
    this.apdService = apdService;
    this.catalogueClient = catalogueClient;
  }

  /**
   * Check if user with consumer role has access to requested resource by calling the concerned APD.
   *
   * @param user the {@link User} object
   * @param request the token request
   * @param resource the requested resource
   * @return JsonObject containing info for token creation
   */
  Future<JsonObject> verifyConsumerAccess(User user, RequestToken request, ResourceObj resource) {

    if (!user.getResServersForRole(Roles.CONSUMER).contains(resource.getResServerUrl())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE)
              .build();
      return Future.failedFuture(new ComposeException(r));
    }

    /*
     * itemType is in uppercase when it comes from the CatalogueClient, making it lowercase
     * explicitly since in the token request, policy listing it's lowercase
     */
    JsonObject apdContext = new JsonObject();
    apdContext
        .put(CALL_APD_APDURL, resource.getApdUrl())
        .put(CALL_APD_USERID, user.getUserId())
        .put(CALL_APD_ITEM_ID, request.getItemId())
        .put(CALL_APD_ITEM_TYPE, resource.getItemType().toString().toLowerCase())
        .put(CALL_APD_RES_SER_URL, resource.getResServerUrl())
        .put(CALL_APD_OWNERID, resource.getOwnerId().toString())
        .put(CALL_APD_CONTEXT, request.getContext());

    return apdService.callApd(apdContext);
  }

  /**
   * Check if user with provider role has access to requested resource.
   *
   * @param user the {@link User} object
   * @param request the token request
   * @param resource the requested resource
   * @return JsonObject containing info for token creation
   */
  Future<JsonObject> verifyProviderAccess(User user, RequestToken request, ResourceObj resource) {
    Promise<JsonObject> p = Promise.promise();

    if (!user.getResServersForRole(Roles.PROVIDER).contains(resource.getResServerUrl())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    if (!resource.getOwnerId().equals(UUID.fromString(user.getUserId()))) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(NOT_RES_OWNER)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    if (resource.isPii()) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    JsonObject details = new JsonObject();
    details.put(STATUS, SUCCESS);
    details.put(CAT_ID, request.getItemId());
    details.put(URL, resource.getResServerUrl());
    p.complete(details);

    return p.future();
  }

  /**
   * Check if delegate has access to resource on behalf of delegator. Based on the kind of
   * delegation, {@link PolicyServiceImpl#verifyConsumerAccess(User, RequestToken, ResourceObj)} or
   * {@link PolicyServiceImpl#verifyProviderAccess(User, RequestToken, ResourceObj)} is called.
   *
   * @param user the User object of the delegate
   * @param request the token request
   * @param delegInfo information about the delegation
   * @param resource the requested resource
   * @return JsonObject containing information to create token
   */
  Future<JsonObject> verifyDelegateAccess(
      User user, RequestToken request, DelegationInformation delegInfo, ResourceObj resource) {
    Promise<JsonObject> p = Promise.promise();

    if (!delegInfo.getDelegatedRsUrl().equals(resource.getResServerUrl())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    /*
     * Based on the delegation information, create a User object with the delegator's user ID, the
     * delegated role and the delegated resource server
     */
    Roles delegatedRole = delegInfo.getDelegatedRole();
    Map<String, JsonArray> delegatedRoletoRs =
        Map.of(delegatedRole.toString(), new JsonArray().add(delegInfo.getDelegatedRsUrl()));

    UserBuilder builder =
        new UserBuilder()
            .roles(List.of(delegatedRole))
            .rolesToRsMapping(delegatedRoletoRs)
            .userId(delegInfo.getDelegatorUserId());
    User delegatedUser = builder.build();

    Future<JsonObject> delegatedAction;

    if (delegatedRole.equals(Roles.CONSUMER)) {
      delegatedAction = verifyConsumerAccess(delegatedUser, request, resource);
    } else if (delegatedRole.equals(Roles.PROVIDER)) {
      delegatedAction = verifyProviderAccess(delegatedUser, request, resource);
    } else {
      delegatedAction =
          Future.failedFuture(
              "Delegated resource access strategy not defined for role "
                  + delegatedRole.toString());
    }

    delegatedAction
        .compose(
            json -> {
              json.put(CREATE_TOKEN_DID, delegInfo.getDelegatorUserId());
              json.put(CREATE_TOKEN_DRL, delegInfo.getDelegatedRole().toString());

              return Future.succeededFuture(json);
            })
        .onSuccess(succ -> p.complete(succ))
        .onFailure(fail -> p.fail(fail));

    return p.future();
  }

  @Override
  public Future<JsonObject> verifyResourceAccess(
      RequestToken request, DelegationInformation delegInfo, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    String itemIdStr = request.getItemId();
    Roles role = request.getRole();

    if (role.equals(Roles.ADMIN) || role.equals(Roles.COS_ADMIN)) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ACCESS_DENIED)
              .detail(INVALID_ROLE)
              .build();
      promiseHandler.fail(new ComposeException(r));
      return promiseHandler.future();
    }

    if (!itemIdStr.matches(UUID_REGEX)) {
      Response r =
          new ResponseBuilder()
              .status(400)
              .type(URN_INVALID_INPUT)
              .title(INVALID_INPUT)
              .detail(INCORRECT_ITEM_ID)
              .build();
      promiseHandler.fail(new ComposeException(r));
      return promiseHandler.future();
    }

    UUID itemId = UUID.fromString(itemIdStr);
    Future<ResourceObj> resourceDetails = catalogueClient.getResourceDetails(itemId);

    Future<JsonObject> verifyAccessByRole =
        resourceDetails.compose(
            res -> {
              if (role.equals(Roles.PROVIDER)) {
                return verifyProviderAccess(user, request, res);
              } else if (role.equals(Roles.CONSUMER)) {
                return verifyConsumerAccess(user, request, res);
              } else if (role.equals(Roles.DELEGATE)) {
                return verifyDelegateAccess(user, request, delegInfo, res);
              } else {
                return Future.failedFuture(
                    "Resource access strategy not defined for role " + role.toString());
              }
            });

    verifyAccessByRole
        .onSuccess(
            s -> {
              s.put(CREATE_TOKEN_RG, resourceDetails.result().getResGrpId().toString());
              promiseHandler.complete(s);
            })
        .onFailure(
            e -> {
              if (e instanceof ComposeException) {
                promiseHandler.fail(e);
                return;
              }

              LOGGER.error("Access evaluation failed : {}", e.getMessage());
              promiseHandler.fail(INTERNALERROR);
            });

    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> listDelegation(User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!(user.getRoles().contains(Roles.PROVIDER)
        || user.getRoles().contains(Roles.CONSUMER)
        || user.getRoles().contains(Roles.DELEGATE))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> data =
        pool.withConnection(
            conn ->
                conn.preparedQuery(LIST_DELEGATION_AS_DELEGATOR_OR_DELEGATE)
                    .collecting(collect)
                    .execute(Tuple.of(UUID.fromString(user.getUserId())))
                    .map(res -> res.value()));

    Future<JsonObject> userInfo =
        data.compose(
            result -> {
              Set<String> ss = new HashSet<String>();
              result.forEach(
                  obj -> {
                    ss.add(obj.getString("delegator_id"));
                    ss.add(obj.getString("user_id"));
                  });

              return registrationService.getUserDetails(new ArrayList<String>(ss));
            });

    userInfo
        .onSuccess(
            results -> {
              List<JsonObject> deleRes = data.result();
              Map<String, JsonObject> details = jsonObjectToMap.apply(results);

              deleRes.forEach(
                  obj -> {
                    JsonObject ownerDet = details.get(obj.getString("delegator_id"));
                    ownerDet.put("id", obj.remove("delegator_id"));

                    JsonObject userDet = details.get(obj.getString("user_id"));
                    userDet.put("id", obj.remove("user_id"));

                    obj.put("owner", ownerDet);
                    obj.put("user", userDet);
                  });
              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_LIST_DELEGS)
                      .arrayResults(new JsonArray(deleRes))
                      .status(200)
                      .build();
              promiseHandler.complete(r.toJson());
            })
        .onFailure(
            e -> {
              LOGGER.error(e.getMessage());
              promiseHandler.fail("Internal error");
            });
    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> deleteDelegation(List<DeleteDelegationRequest> request, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!user.getRoles().contains(Roles.PROVIDER) && !user.getRoles().contains(Roles.CONSUMER)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_DEL_DELEGATE_ROLES)
              .status(401)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    // ids will be unique - OpenAPI takes care of duplicates
    List<UUID> ids =
        request.stream().map(obj -> UUID.fromString(obj.getId())).collect(Collectors.toList());

    Tuple queryTup =
        Tuple.of(UUID.fromString(user.getUserId())).addArrayOfUUID(ids.toArray(UUID[]::new));

    Collector<Row, ?, Set<UUID>> collect =
        Collectors.mapping(row -> row.getUUID("id"), Collectors.toSet());

    Future<Set<UUID>> idServerMap =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_DELEGATIONS_BY_ID)
                    .collecting(collect)
                    .execute(queryTup)
                    .map(res -> res.value()));

    Future<Void> validate =
        idServerMap.compose(
            data -> {
              if (data.size() != ids.size()) {
                List<UUID> badIds =
                    ids.stream().filter(id -> !data.contains(id)).collect(Collectors.toList());

                return Future.failedFuture(
                    new ComposeException(
                        400, URN_INVALID_INPUT, ERR_TITLE_INVALID_ID, badIds.get(0).toString()));
              }
              return Future.succeededFuture();
            });

    validate
        .compose(
            i ->
                pool.withTransaction(
                    conn ->
                        conn.preparedQuery(DELETE_DELEGATIONS)
                            .execute(Tuple.of(ids.toArray(UUID[]::new)))))
        .onSuccess(
            res -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_DELETE_DELE)
                      .objectResults(new JsonObject())
                      .status(200)
                      .build();
              promiseHandler.complete(r.toJson());
              LOGGER.info("Deleted delegations {}", ids.toString());
            })
        .onFailure(
            e -> {
              if (e instanceof ComposeException) {
                ComposeException exp = (ComposeException) e;
                promiseHandler.complete(exp.getResponse().toJson());
                return;
              }
              LOGGER.error(e.getMessage());
              promiseHandler.fail("Internal error");
            });

    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> createDelegation(List<CreateDelegationRequest> request, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!(user.getRoles().contains(Roles.PROVIDER) || user.getRoles().contains(Roles.CONSUMER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_CREATE_DELEGATE_ROLES)
              .status(401)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    // check if the (role + resource server) for a delegation is owned by the user
    List<String> rsRoleNotOwnedByUser =
        request.stream()
            .filter(obj -> !user.getResServersForRole(obj.getRole()).contains(obj.getResSerUrl()))
            .map(obj -> obj.getResSerUrl())
            .collect(Collectors.toList());

    if (!rsRoleNotOwnedByUser.isEmpty()) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE)
              .detail(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE)
              .errorContext(
                  new JsonObject()
                      .put(
                          ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                          new JsonArray(rsRoleNotOwnedByUser)))
              .status(400)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    Set<String> userEmails =
        request.stream().map(obj -> obj.getUserEmail()).collect(Collectors.toSet());

    List<String> requestedResServers =
        request.stream().map(obj -> obj.getResSerUrl()).collect(Collectors.toList());

    Future<JsonObject> checkUsersExist = registrationService.findUserByEmail(userEmails);

    Collector<Row, ?, Map<Pair<Roles, String>, UUID>> roleRsToRoleIdCollector =
        Collectors.toMap(
            row -> Pair.of(row.get(Roles.class, "role"), row.getString("url")),
            row -> row.getUUID("id"));

    String[] rolesArr = user.getRoles().stream().map(i -> i.toString()).toArray(String[]::new);
    Tuple tup =
        Tuple.of(user.getUserId())
            .addArrayOfString(rolesArr)
            .addArrayOfString(requestedResServers.toArray(String[]::new));

    // using a pair here since we need a combo of role + RS URL to map to the associated role ID
    Future<Map<Pair<Roles, String>, UUID>> getRoleIds =
        checkUsersExist.compose(
            res ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_ROLE_IDS_BY_ROLE_AND_RS)
                            .collecting(roleRsToRoleIdCollector)
                            .execute(tup)
                            .map(succ -> succ.value())));

    Future<List<Tuple>> createTuples =
        getRoleIds.compose(
            roleMap -> {
              Map<String, JsonObject> userMap = jsonObjectToMap.apply(checkUsersExist.result());
              List<Tuple> tups = new ArrayList<Tuple>();

              request.forEach(
                  obj -> {
                    UUID delegateId =
                        UUID.fromString(userMap.get(obj.getUserEmail()).getString("keycloakId"));
                    UUID roleId = roleMap.get(Pair.of(obj.getRole(), obj.getResSerUrl()));
                    tups.add(Tuple.of(delegateId, roleId, DelegationStatus.ACTIVE));
                  });

              return Future.succeededFuture(tups);
            });

    Future<Void> checkDuplicatesAndInsert =
        createTuples.compose(
            tuples -> {
              return pool.withTransaction(
                  conn ->
                      conn.preparedQuery(CHECK_EXISTING_DELEGATIONS)
                          .executeBatch(tuples)
                          .compose(
                              ar -> {
                                // This check to get response when batch query is executed for
                                // select
                                RowSet<Row> rows = ar;
                                List<UUID> ids = new ArrayList<>();
                                while (rows != null) {
                                  rows.iterator()
                                      .forEachRemaining(
                                          row -> {
                                            ids.add(row.getUUID(ID));
                                          });
                                  rows = rows.next();
                                }
                                if (!ids.isEmpty()) {
                                  Response r =
                                      new Response.ResponseBuilder()
                                          .type(URN_ALREADY_EXISTS)
                                          .title(ERR_TITLE_DUPLICATE_DELEGATION)
                                          .detail(ERR_DETAIL_DUPLICATE_DELEGATION)
                                          .errorContext(
                                              new JsonObject()
                                                  .put(
                                                      ERR_CONTEXT_EXISTING_DELEGATION_IDS,
                                                      new JsonArray(
                                                          ids.stream()
                                                              .map(x -> x.toString())
                                                              .collect(Collectors.toList()))))
                                          .status(409)
                                          .build();

                                  return Future.failedFuture(new ComposeException(r));
                                }
                                return Future.succeededFuture(tuples);
                              })
                          .compose(
                              tups ->
                                  conn.preparedQuery(INSERT_DELEGATION)
                                      .executeBatch(tups)
                                      .mapEmpty()));
            });

    checkDuplicatesAndInsert
        .onSuccess(
            succ -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title("added delegations")
                      .status(201)
                      .build();
              promiseHandler.complete(r.toJson());
            })
        .onFailure(
            obj -> {
              if (obj instanceof ComposeException) {
                ComposeException e = (ComposeException) obj;
                promiseHandler.complete(e.getResponse().toJson());
                return;
              }

              LOGGER.error("Create delegation failed : {}", obj.getMessage());
              promiseHandler.fail(INTERNALERROR);
            });

    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> getDelegateEmails(
      User user, String delegatorUserId, Roles delegatedRole, String delegatedRsUrl) {

    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!user.getRoles().contains(Roles.TRUSTEE)) {
      Response r =
          new ResponseBuilder()
              .status(401)
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_NOT_TRUSTEE)
              .detail(ERR_DETAIL_NOT_TRUSTEE)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    Collector<Row, ?, Set<UUID>> delegateUserIdCollector =
        Collectors.mapping(row -> row.getUUID("user_id"), Collectors.toSet());

    Tuple tuple = Tuple.of(UUID.fromString(delegatorUserId), delegatedRole, delegatedRsUrl);

    Future<Set<UUID>> check =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(SQL_GET_DELEG_USER_IDS_BY_DELEGATION_INFO)
                        .collecting(delegateUserIdCollector)
                        .execute(tuple))
            .map(res -> res.value());

    Future<List<String>> uniqDelegIds =
        check.compose(
            ids -> {
              if (!ids.isEmpty()) {
                List<String> strIds =
                    ids.stream().map(id -> id.toString()).collect(Collectors.toList());
                return Future.succeededFuture(strIds);
              }

              // if no delegates, whether user exists or not, return empty JSON array
              Response resp =
                  new ResponseBuilder()
                      .status(200)
                      .type(Urn.URN_SUCCESS)
                      .title(SUCC_TITLE_DELEG_EMAILS)
                      .objectResults(new JsonObject().put(RESP_DELEG_EMAILS, new JsonArray()))
                      .build();
              return Future.failedFuture(new ComposeException(resp));
            });

    Future<JsonObject> getDelegatesInfo =
        uniqDelegIds.compose(ids -> registrationService.getUserDetails(ids));

    Future<JsonArray> delegEmails =
        getDelegatesInfo.compose(
            json -> {
              List<String> emailList =
                  uniqDelegIds.result().stream()
                      .map(id -> json.getJsonObject(id).getString("email"))
                      .collect(Collectors.toList());

              return Future.succeededFuture(new JsonArray(emailList));
            });

    delegEmails
        .onSuccess(
            res -> {
              Response resp =
                  new ResponseBuilder()
                      .status(200)
                      .type(Urn.URN_SUCCESS)
                      .title(SUCC_TITLE_DELEG_EMAILS)
                      .objectResults(new JsonObject().put("delegateEmails", res))
                      .build();
              promiseHandler.complete(resp.toJson());
            })
        .onFailure(
            e -> {
              if (e instanceof ComposeException) {
                ComposeException exp = (ComposeException) e;
                promiseHandler.complete(exp.getResponse().toJson());
                return;
              }
              LOGGER.error(e.getMessage());
              promiseHandler.fail("Internal error");
            });

    return promiseHandler.future();
  }
}
