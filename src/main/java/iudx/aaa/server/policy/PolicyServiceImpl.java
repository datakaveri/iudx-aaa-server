package iudx.aaa.server.policy;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.data.Interval;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.DeletePolicyNotificationRequest;
import iudx.aaa.server.apiserver.NotifRequestStatus;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdatePolicyNotification;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.registration.RegistrationService;
import java.util.Arrays;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_INFO;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.token.Constants.INVALID_POLICY;

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
  private final deletePolicy deletePolicy;
  private final createPolicy createPolicy;
  private final createDelegate createDelegate;
  private final CatalogueClient catalogueClient;
  private final JsonObject authOptions;
  private final JsonObject catServerOptions;
  private final EmailInfo emailInfo = new EmailInfo();

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
      CatalogueClient catalogueClient,
      JsonObject authOptions,
      JsonObject catServerOptions,EmailClient emailClient) {
    this.pool = pool;
    this.registrationService = registrationService;
    this.apdService = apdService;
    this.catalogueClient = catalogueClient;
    this.authOptions = authOptions;
    this.catServerOptions = catServerOptions;
    this.emailClient = emailClient;
    this.deletePolicy = new deletePolicy(pool, authOptions);
    this.createPolicy = new createPolicy(pool, authOptions);
    this.createDelegate = new createDelegate(pool, authOptions);
    this.emailInfo = new EmailInfo();
  }

  @Override
  public PolicyService createPolicy(
      List<CreatePolicyRequest> request,
      User user,
      JsonObject data,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    List<Roles> roles = user.getRoles();
    boolean isDelegate = !data.isEmpty();
    String providerId;
    if (isDelegate) providerId = data.getString("providerId");
    else providerId = user.getUserId();
    // check duplicate
    // same test works for both userpolicies and apdUserpolicies as there can only be one apd_policy for any given resource
    //no need to check if the requests have different apdId/userClass
    List<CreatePolicyRequest> duplicates =
        request.stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getUserId() + "-" + p.getItemId() + "-" + p.getItemType(),
                    Collectors.toList()))
            .values()
            .stream()
            .filter(i -> i.size() > 1)
            .flatMap(j -> j.stream())
            .collect(Collectors.toList());

    if (duplicates.size() > 0) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(DUPLICATE)
              .detail(duplicates.get(0).toString())
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<CreatePolicyRequest> userPolicyRequests =
        request.stream().filter(ar -> ar.getApdId().equals(NIL_UUID)).collect(Collectors.toList());
    List<CreatePolicyRequest> apdPolicyRequests =
        request.stream().filter(ar -> ar.getUserId().equals(NIL_UUID)).collect(Collectors.toList());

    if (!roles.contains(Roles.ADMIN)
        && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE)
        && !roles.contains(Roles.TRUSTEE)) {

      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(INVALID_ROLE)
              .detail(INVALID_ROLE)
              .status(403)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    // apd policies will not have userIDs to check
    Future<Set<UUID>> UserExist;
    if (!userPolicyRequests.isEmpty()) {

      Set<UUID> users =
          userPolicyRequests.stream()
              .map(e -> UUID.fromString(e.getUserId()))
              .collect(Collectors.toSet());
      UserExist = createPolicy.checkUserExist(users);
    } else UserExist = Future.succeededFuture(new HashSet<UUID>());

    // get list of apds from apdPolicyRequests(get apdId) and check with getApdDetails if valid
    Future<JsonObject> validApd;
    if (!apdPolicyRequests.isEmpty()) {
      List<String> urls =
          apdPolicyRequests.stream().map(e -> e.getApdId()).collect(Collectors.toList());
      Promise<JsonObject> promise = Promise.promise();
      apdService.getApdDetails(urls, List.of(), promise);

      validApd = promise.future();
    } else validApd = Future.succeededFuture(new JsonObject());

    List<String> exp =
        request.stream()
            .filter(tagObject -> !tagObject.getExpiryTime().isEmpty())
            .map(CreatePolicyRequest::getExpiryTime)
            .collect(Collectors.toList());

    Future<Void> validateExp;
    if (!exp.isEmpty()) validateExp = createPolicy.validateExpiry(exp);
    else {
      validateExp = Future.succeededFuture();
    }

    List<String> resServerIds =
        userPolicyRequests.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_SERVER.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    // getApdInfo for all apdIds
    // if itemType is apdIds, getApdInfo
    List<String> apdUrls =
        userPolicyRequests.stream()
            .filter(
                tagObject -> tagObject.getItemType().toUpperCase().equals(itemTypes.APD.toString()))
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

    // the format for resource group item id when split by '/' should be of exactly length 4
    if (!resGrpIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length == 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
            .map(CreatePolicyRequest::getItemId)
            .collect(Collectors.toList());

    // the format for resource item id when split by '/' should be of greater than len of resource
    // group(4)
    if (!resIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length > 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }
    Map<String, List<String>> catItem = new HashMap<>();

    // check if resServer itemType, All requests must be resServer, role must contain admin
    // if itemType is Apd, all req must be Apd,role must contain Trustee
    // if  item type neither, for request may have both apd and user policies (catalogueFetch)
    if (resServerIds.size() > 0) {
      // if request has itemType resourceServer, then all request should be for resource server
      if (resServerIds.size() != request.size()) {
        Response r =
            new Response.ResponseBuilder()
                .type(URN_INVALID_INPUT)
                .title(INVALID_INPUT)
                .detail("All requests must be for resource server")
                .status(400)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      }
      if (!roles.contains(Roles.ADMIN)) {
        Response r =
            new Response.ResponseBuilder()
                .type(URN_INVALID_ROLE)
                .title(INVALID_ROLE)
                .detail(INVALID_ROLE)
                .status(403)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      } else catItem.put(RES_SERVER, resServerIds);
    } else {
      // check if user policy for apd exists
      if (apdUrls.size() > 0) {
        if (apdUrls.size() != request.size()) {
          Response r =
              new Response.ResponseBuilder()
                  .type(URN_INVALID_INPUT)
                  .title(INVALID_INPUT)
                  .detail("All requests must be for APD")
                  .status(400)
                  .build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return this;
        }
        if (!roles.contains(Roles.TRUSTEE)) {
          Response r =
              new Response.ResponseBuilder()
                  .type(URN_INVALID_ROLE)
                  .title(INVALID_ROLE)
                  .detail(INVALID_ROLE)
                  .status(403)
                  .build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return this;
        }
        catItem.put(APD, apdUrls);
      } else {
        if (!roles.contains(Roles.PROVIDER) && !roles.contains(Roles.DELEGATE)) {
          Response r =
              new Response.ResponseBuilder()
                  .type(URN_INVALID_ROLE)
                  .title(INVALID_ROLE)
                  .detail(INVALID_ROLE)
                  .status(403)
                  .build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return this;
        }
        if (resGrpIds.size() > 0) catItem.put(RES_GRP, resGrpIds);
        if (resIds.size() > 0) catItem.put(RES, resIds);
      }
    }
    Future<Map<String, ResourceObj>> reqItemDetail;
    if (catItem.containsKey(RES_SERVER)) {
      reqItemDetail = createPolicy.getResSerDetails(catItem.get(RES_SERVER), user.getUserId());
    } else {
      if (catItem.containsKey(APD)) {
        List<String> urls = catItem.get(APD);
        Promise<JsonObject> promise = Promise.promise();
        apdService.getApdDetails(urls, List.of(), promise);
        reqItemDetail =
            promise
                .future()
                .compose(
                    apdDetail -> {
                      Map<String, ResourceObj> apdMap = new HashMap<>();
                      List<String> failedUrl = new ArrayList<>();
                      urls.forEach(
                          url -> {
                            if (!apdDetail.containsKey(url)) failedUrl.add(url);
                            else {
                              JsonObject detail = apdDetail.getJsonObject(url);
                              //status of the apd is not validated for creating policy by the trustee
                                JsonObject resObj = new JsonObject();
                                resObj.put(ITEMTYPE, APD);
                                resObj.put(ID, detail.getString(ID));
                                resObj.put(CAT_ID, detail.getString(URL));
                                resObj.put(
                                    OWNER_ID, detail.getJsonObject(OWNER_DETAILS).getString(ID));
                                resObj.put("resource_server_id",NIL_UUID);
                                resObj.put("resource_group_id",NIL_UUID);
                                apdMap.put(resObj.getString(CAT_ID), new ResourceObj(resObj));
                            }
                          });

                      if (failedUrl.size() > 0) {
                        Response r =
                            new ResponseBuilder()
                                .status(400)
                                .type(URN_INVALID_INPUT)
                                .title(INVALID_INPUT)
                                .detail(failedUrl.toString())
                                .build();
                        return Future.failedFuture(new ComposeException(r));
                      }
                      return Future.succeededFuture(apdMap);
                    });
      } else // For both apdPolicy and userPolicy
      reqItemDetail = catalogueClient.checkReqItems(catItem);
    }

    Future<Boolean> ItemChecks =
        CompositeFuture.all(UserExist, validateExp, reqItemDetail, validApd)
            .compose(
                obj -> {
                  if (!UserExist.result().isEmpty()) {
                    LOGGER.debug("UserExist fail:: " + UserExist.result().toString());
                    Response r =
                        new ResponseBuilder()
                            .status(400)
                            .type(URN_INVALID_INPUT)
                            .title(INVALID_USER)
                            .detail(UserExist.result().iterator().next().toString())
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  }

                  List<String> urls =
                          apdPolicyRequests.stream().map(CreatePolicyRequest::getApdId).collect(Collectors.toList());

                  List<String> invalidUrl = new ArrayList<>();
                  urls.forEach(url ->
                  {
                    if(!validApd.result().getJsonObject(url).getString(STATUS).equals(ApdStatus.ACTIVE.toString().toLowerCase()))
                      invalidUrl.add(url);
                  });

                  if(!invalidUrl.isEmpty())
                  {
                    Response r =
                            new ResponseBuilder()
                                    .status(400)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_APD_STATUS)
                                    .detail(invalidUrl.toString())
                                    .build();
                    return Future.failedFuture(new ComposeException(r));
                  }
                 if (catItem.containsKey(RES_SERVER)) return Future.succeededFuture(false);

                  return Future.succeededFuture(true);
                });

    Future<Boolean> checkAuthPolicy =
        ItemChecks.compose(
            obj -> {
              if (ItemChecks.result().equals(false)) return Future.succeededFuture(false);
              return createPolicy.checkAuthPolicy(user.getUserId());
            });

    // to create a policy in the apd_polcies table, user must have a policy by the dataTrustee for the apdId
    Future<Boolean> checkTrusteeAuthPolicy =
            ItemChecks.compose(obj ->
                    {
                        if(validApd.result().isEmpty())
                          return Future.succeededFuture(true);
                        else
                        {
                          Set<UUID> apdIds = new HashSet<UUID>();
                          List<String> urls =
                                  apdPolicyRequests.stream().map(CreatePolicyRequest::getApdId).collect(Collectors.toList());
                          urls.forEach(url ->
                          {
                            apdIds.add(UUID.fromString(validApd.result().getJsonObject(url).getString(ID)));
                          });
                          return createPolicy.checkAuthTrusteePolicy(providerId, apdIds);
                        }
                    }
            );


    Future<List<UUID>> checkDelegate = CompositeFuture.all(checkAuthPolicy,checkTrusteeAuthPolicy).compose(
            checkAut -> {
              if (checkAut.equals(false)) return Future.succeededFuture(new ArrayList<>());
              List<ResourceObj> resourceObj = new ArrayList<>(reqItemDetail.result().values());

              List<UUID> owners =
                  resourceObj.stream()
                      .distinct()
                      .map(ResourceObj::getOwnerId)
                      .collect(Collectors.toList());

              List<UUID> owned =
                  owners.stream()
                      .filter(x -> !x.equals(UUID.fromString(providerId)))
                      .collect(Collectors.toList());

              if (owned.isEmpty()) {
                return Future.succeededFuture(new ArrayList<>());
              } else {
                Response r =
                    new Response.ResponseBuilder()
                        .type(URN_INVALID_INPUT)
                        .title(UNAUTHORIZED)
                        .detail(UNAUTHORIZED)
                        .status(403)
                        .build();
                return Future.failedFuture(new ComposeException(r));
              }
            });


    Future<List<Tuple>>  checkUserPolicyDuplicate;
    if(userPolicyRequests.isEmpty())
      checkUserPolicyDuplicate =  checkDelegate.compose(
              succ ->  Future.succeededFuture(List.of()));
    else
      checkUserPolicyDuplicate =  checkDelegate.compose(
              succ -> createPolicy.userPolicyDuplicate(userPolicyRequests,reqItemDetail.result(),user));

    Future<List<Tuple>>  checkApdPolicyDuplicate;
    if(apdPolicyRequests.isEmpty())
      checkApdPolicyDuplicate =  checkDelegate.compose(
              succ ->  Future.succeededFuture(List.of()));
    else
      checkApdPolicyDuplicate =  checkDelegate.compose(
              succ -> createPolicy.apdPolicyDuplicate(apdPolicyRequests,reqItemDetail.result(),validApd.result()));

    // use only reqItemDetail to insert items

    Future<CompositeFuture> insertPolicy = CompositeFuture.all(checkUserPolicyDuplicate,checkApdPolicyDuplicate).compose(tup ->
    {
      Future<Boolean> insertUserPolicy;
      if(checkUserPolicyDuplicate.result().isEmpty())
        insertUserPolicy =  Future.succeededFuture(true);
      else
        insertUserPolicy =   createPolicy.insertPolicy(INSERT_POLICY,checkUserPolicyDuplicate.result());

      Future<Boolean> insertApdPolicy;
      if(checkApdPolicyDuplicate.result().isEmpty())
        insertApdPolicy =  Future.succeededFuture(true);
      else
        insertApdPolicy =   createPolicy.insertPolicy(INSERT_APD_POLICY,checkApdPolicyDuplicate.result());

         return CompositeFuture.all(insertUserPolicy,insertApdPolicy);
    });

    insertPolicy.onSuccess(succ -> {
      Response r =
              new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title("added policies")
                      .status(200)
                      .build();
      handler.handle(Future.succeededFuture(r.toJson()));})
            .onFailure(
                    obj -> {
                      LOGGER.error(obj.getMessage());
                      if (obj instanceof ComposeException) {
                        ComposeException e = (ComposeException) obj;
                        handler.handle(Future.succeededFuture(e.getResponse().toJson()));
                      } else handler.handle(Future.failedFuture(INTERNALERROR));
                    });
    return this;
  }

  @Override
  public PolicyService deletePolicy(JsonArray request, User user, JsonObject data,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      // empty user object
      Response r = new Response.ResponseBuilder().type(URN_MISSING_INFO)
          .title(String.valueOf(URN_MISSING_INFO)).detail(NO_USER).status(401).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<Roles> roles = user.getRoles();

    if (!roles.contains(Roles.ADMIN) && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE) && ! roles.contains(Roles.TRUSTEE)) {
      // cannot create policy
      Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).status(401).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<UUID> req = request.stream().map(JsonObject.class::cast)
        .filter(tagObject -> !tagObject.getString(ID).isEmpty())
        .map(tagObject -> UUID.fromString(tagObject.getString(ID))).collect(Collectors.toList());

    Future<Boolean> deletedPolicies = deletePolicy.checkPolicyExist(req, user, data)
        .compose(policyIdsMap -> deletePolicy.delPolicy(policyIdsMap));

    deletedPolicies.onSuccess(resp -> {
      Response r = new Response.ResponseBuilder().type(URN_SUCCESS).title(SUCC_TITLE_POLICY_DEL)
          .status(200).build();
      handler.handle(Future.succeededFuture(r.toJson()));
    }).onFailure(obj -> {
      if (obj instanceof ComposeException) {
        ComposeException e = (ComposeException) obj;
        handler.handle(Future.succeededFuture(e.getResponse().toJson()));
        return;
      }
      LOGGER.error(obj.getMessage());
      handler.handle(Future.failedFuture(INTERNALERROR));
    });
    return this;
  }

  @Override
  public PolicyService listPolicy(User user, JsonObject data,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      // empty user object
      Response r = new Response.ResponseBuilder().type(URN_MISSING_INFO)
          .title(String.valueOf(URN_MISSING_INFO)).detail(NO_USER).status(404).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    boolean isDelegate = false;
    String policyTableQuery;
    UUID providerId;

    isDelegate = !data.isEmpty();
    if (isDelegate) {
      providerId = UUID.fromString(data.getString("providerId"));
      policyTableQuery = GET_USER_POLICIES_AUTH_DELEGATE;
    } else {
      providerId = UUID.fromString(user.getUserId());
      policyTableQuery = GET_USER_POLICIES;
    }

    Collector<Row, ?, List<JsonObject>> policyCollector =
        Collectors.mapping(Row::toJson, Collectors.toList());

    Future<List<JsonObject>> userPolicies =
        pool.withConnection(conn -> conn.preparedQuery(policyTableQuery).collecting(policyCollector)
            .execute(Tuple.of(providerId)).map(SqlResult::value));

    Future<List<JsonObject>> apdPolicies =
        pool.withConnection(conn -> conn.preparedQuery(GET_APD_POLICIES).collecting(policyCollector)
            .execute(Tuple.of(providerId)).map(SqlResult::value));

    Future<CompositeFuture> allDetails =
        CompositeFuture.all(userPolicies, apdPolicies).compose(obj -> {
          List<JsonObject> allPolicies = new ArrayList<>();
          allPolicies.addAll(userPolicies.result());
          allPolicies.addAll(apdPolicies.result());

          /*
           * Using groupingBy, we create a map with key being itemTypes, and value being the
           * corresponding list of itemIds. The first part of groupingBy groups the different JSON
           * objects in policies by itemTypes. the second part extracts only the itemId from the
           * JSON object, converts to UUID, and forms the UUID list.
           */
          Map<itemTypes, Set<UUID>> itemTypeToIds = allPolicies.stream().collect(
              Collectors.groupingBy(j -> itemTypes.valueOf(j.getString(ITEMTYPE)), Collectors
                  .mapping(res -> UUID.fromString(res.getString(ITEM_ID)), Collectors.toSet())));

          for (itemTypes i : itemTypes.values()) {
            if (!itemTypeToIds.containsKey(i)) {
              itemTypeToIds.put(i, new HashSet<UUID>());
            }
          }

          Set<String> userIdSet =
              allPolicies.stream().filter(tagObject -> tagObject.containsKey(USER_ID))
                  .map(tagObject -> tagObject.getString(USER_ID)).collect(Collectors.toSet());

          userIdSet.addAll(allPolicies.stream().filter(tagObject -> tagObject.containsKey(OWNER_ID))
              .map(tagObject -> tagObject.getString(OWNER_ID)).collect(Collectors.toSet()));

          List<String> userIds = new ArrayList<String>(userIdSet);

          /*
           * For APD IDs get IDs from policies where the item type is APD and from the APD IDs in
           * APD policies
           */
          Set<String> apdIdSet = itemTypeToIds.get(itemTypes.APD).stream().map(id -> id.toString())
              .collect(Collectors.toSet());
          apdIdSet.addAll(apdPolicies.result().stream().map(j -> j.getString(APD_ID))
              .collect(Collectors.toSet()));

          List<String> apdIds = new ArrayList<String>(apdIdSet);

          Promise<JsonObject> userDetails = Promise.promise();
          registrationService.getUserDetails(userIds, userDetails);

          Promise<JsonObject> apdDetails = Promise.promise();
          if (!apdIds.isEmpty()) {
            apdService.getApdDetails(List.of(), apdIds, apdDetails);
          } else {
            apdDetails.complete(new JsonObject());
          }

          Future<Map<itemTypes, Map<UUID, String>>> resourceDetails =
              getUrlAndCatIds(itemTypeToIds);

          return CompositeFuture.all(userDetails.future(), apdDetails.future(), resourceDetails);
        });

    allDetails.onSuccess(success -> {
      JsonArray result = new JsonArray();
      JsonObject userDetails = (JsonObject) success.result().list().get(0);
      Map<String, JsonObject> userDetailsMap = jsonObjectToMap.apply(userDetails);

      JsonObject apdDetails = (JsonObject) success.result().list().get(1);
      Map<String, JsonObject> apdDetailsMap = jsonObjectToMap.apply(apdDetails);

      @SuppressWarnings("unchecked")
      Map<itemTypes, Map<UUID, String>> allResDetails =
          (Map<itemTypes, Map<UUID, String>>) success.result().list().get(2);

      Map<UUID, String> resDetails = allResDetails.get(itemTypes.RESOURCE);
      Map<UUID, String> resGrpDetails = allResDetails.get(itemTypes.RESOURCE_GROUP);
      Map<UUID, String> resSerDetails = allResDetails.get(itemTypes.RESOURCE_SERVER);

      List<JsonObject> allPolicies = new ArrayList<>();
      allPolicies.addAll(userPolicies.result());
      allPolicies.addAll(apdPolicies.result());

      for (JsonObject obj : allPolicies) {

        String itemType = obj.getString(ITEMTYPE);
        obj.put("itemType", itemType.toLowerCase());
        String itemId = (String) obj.remove(ITEM_ID);
        switch (itemTypes.valueOf(itemType)) {
          case RESOURCE_SERVER:
            obj.put(ITEMID, resSerDetails.get(UUID.fromString(itemId)));
            break;
          case RESOURCE_GROUP:
            obj.put(ITEMID, resGrpDetails.get(UUID.fromString(itemId)));
            break;
          case RESOURCE:
            obj.put(ITEMID, resDetails.get(UUID.fromString(itemId)));
            break;
          case APD:
            obj.put(ITEMID, apdDetailsMap.get(itemId).getString(URL));
            break;
        }

        if (obj.containsKey(USER_ID)) {
          String userId = (String) obj.remove(USER_ID);
          JsonObject details = userDetailsMap.get(userId);
          details.put(ID, userId);
          obj.put(USER_DETAILS, details);
        }

        if (obj.containsKey(OWNER_ID)) {
          String userId = (String) obj.remove(OWNER_ID);
          JsonObject details = userDetailsMap.get(userId);
          details.put(ID, userId);
          obj.put(OWNER_DETAILS, details);
        }

        if (obj.containsKey(APD_ID)) {
          String apdId = (String) obj.remove(APD_ID);
          JsonObject details = apdDetailsMap.get(apdId);
          details.put(ID, apdId);
          obj.put(APD_DETAILS, details);
        }

        result.add(obj);
      }

      Response r = new Response.ResponseBuilder().type(URN_SUCCESS).title(SUCC_TITLE_POLICY_READ)
          .status(200).arrayResults(result).build();
      handler.handle(Future.succeededFuture(r.toJson()));
    }).onFailure(obj -> {
      LOGGER.error(obj.getMessage());
      handler.handle(Future.failedFuture(INTERNALERROR));
    });
    return this;
  }
  
  /**
   * Get cat IDs for resource and resource group items and URLs for resource servers using item IDs.
   * 
   * @param itemIds a Map of itemTypes to a set of item IDs in UUID. RESOURCE, RESOURCE_SERVER and
   *        RESOURCE_GROUP are handled
   * @return a future. A map of itemType that maps to another map containing item IDs mapped to the
   *         cat ID/URL
   */
  private Future<Map<itemTypes, Map<UUID, String>>> getUrlAndCatIds(
      Map<itemTypes, Set<UUID>> itemIds) {
    Promise<Map<itemTypes, Map<UUID, String>>> promise = Promise.promise();

    Set<UUID> resSerItemIds = itemIds.get(itemTypes.RESOURCE_SERVER);
    Set<UUID> resGrpItemIds = itemIds.get(itemTypes.RESOURCE_GROUP);
    Set<UUID> resItemIds = itemIds.get(itemTypes.RESOURCE);

    Future<Map<UUID, String>> resGrpCatIds =
        catalogueClient.getCatIds(resGrpItemIds, itemTypes.RESOURCE_GROUP);
    Future<Map<UUID, String>> resCatIds = catalogueClient.getCatIds(resItemIds, itemTypes.RESOURCE);

    Collector<Row, ?, Map<UUID, String>> collector =
        Collectors.toMap(row -> row.getUUID(ID), row -> row.getString(URL));

    Future<Map<UUID, String>> resSerUrls =
        pool.withConnection(conn -> conn.preparedQuery(GET_RES_SER_URLS).collecting(collector)
            .execute(Tuple.of(resSerItemIds.toArray(UUID[]::new))).map(res -> res.value()));

    CompositeFuture.all(resGrpCatIds, resCatIds, resSerUrls).onSuccess(res -> {

      Map<itemTypes, Map<UUID, String>> resultMap = new HashMap<itemTypes, Map<UUID, String>>();

      resultMap.put(itemTypes.RESOURCE_SERVER, resSerUrls.result());
      resultMap.put(itemTypes.RESOURCE_GROUP, resGrpCatIds.result());
      resultMap.put(itemTypes.RESOURCE, resCatIds.result());
      promise.complete(resultMap);
    }).onFailure(err -> {
      LOGGER.error(err.getMessage());
      promise.fail(INTERNALERROR);
    });

    return promise.future();
  }

  Future<JsonObject> verifyConsumerPolicy(UUID userId, String itemId, String itemType,
      JsonObject context, Map<String, ResourceObj> resDetails) {

    Promise<JsonObject> p = Promise.promise();

    /*
     * check itemType, if resGrp check only resGrp table else get resGrp from item id and check both
     * res and resGrp tables as there may be a policy for the resGrp the res belongs to
     */

    Future<JsonObject> getResGrpConstraints;
    Tuple resGroupTuple;

    if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
      resGroupTuple =
          Tuple.of(userId, resDetails.get(itemId).getId(), itemTypes.RESOURCE_GROUP, status.ACTIVE);
    } else {

      resGroupTuple = Tuple.of(userId, resDetails.get(itemId).getResGrpId(),
          itemTypes.RESOURCE_GROUP, status.ACTIVE);
    }
    getResGrpConstraints = pool.withConnection(
        conn -> conn.preparedQuery(GET_CONSUMER_USER_POL_CONSTRAINTS).execute(resGroupTuple)
            .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));

    Future<JsonObject> getResItemConstraints;

    if (itemType.equals(itemTypes.RESOURCE.toString())) {
      getResItemConstraints =
          pool.withConnection(conn -> conn.preparedQuery(GET_CONSUMER_USER_POL_CONSTRAINTS)
              .execute(Tuple.of(userId, resDetails.get(itemId).getId(), itemTypes.RESOURCE,
                  status.ACTIVE))
              .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
    } else {
      getResItemConstraints = Future.succeededFuture(new JsonObject());
    }

    Future<JsonObject> getUserPolicyConstraints =
        CompositeFuture.all(getResGrpConstraints, getResItemConstraints).compose(ar -> {
          if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
            return getResGrpConstraints;
          } else {
            if (getResItemConstraints.result() == null)
              return getResGrpConstraints;
            else {
              return getResItemConstraints;
            }
          }
        });

    Future<JsonObject> getApdPolicyDetails = getUserPolicyConstraints.compose(userPol -> {
      if (userPol != null) {
        return Future.succeededFuture(null);
      }

      Future<JsonObject> resGrpApdPolicy;
      Future<JsonObject> resItemApdPolicy;
      Tuple tuple;

      if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
        tuple =
            Tuple.of(resDetails.get(itemId).getId(), itemTypes.RESOURCE_GROUP, status.ACTIVE);
      } else {
        tuple =
            Tuple.of(resDetails.get(itemId).getResGrpId(), itemTypes.RESOURCE_GROUP, status.ACTIVE);
      }

      /* NOTE: Not checking for APD policy expiry in the queries */
      resGrpApdPolicy = pool.withConnection(
          conn -> conn.preparedQuery(GET_CONSUMER_APD_POL_DETAILS).execute(tuple)
              .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));

      if (itemType.equals(itemTypes.RESOURCE.toString())) {
        resItemApdPolicy =
            pool.withConnection(conn -> conn.preparedQuery(GET_CONSUMER_APD_POL_DETAILS)
                .execute(
                    Tuple.of(resDetails.get(itemId).getId(), itemTypes.RESOURCE, status.ACTIVE))
                .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
      } else {
        resItemApdPolicy = Future.succeededFuture(new JsonObject());
      }

      return CompositeFuture.all(resGrpApdPolicy, resItemApdPolicy).compose(ar -> {
        if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
          return resGrpApdPolicy;
        } else {
          if (resItemApdPolicy.result() == null)
            return resGrpApdPolicy;
          else {
            return resItemApdPolicy;
          }
        }
      });
    });

    Future<String> getUrl = pool
        .withConnection(conn -> conn.preparedQuery(GET_URL)
            .execute(Tuple.of(resDetails.get(itemId).getResServerID()))
            .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getString(URL) : null))
        .compose(ar -> {
          if (ar == null) {
            Response r = new ResponseBuilder().status(403).type(URN_INVALID_INPUT)
                .title(INVALID_POLICY).detail(NO_RES_SERVER).build();
            return Future.failedFuture(new ComposeException(r));
          } else {
            return Future.succeededFuture(ar);
          }
        });

    CompositeFuture.all(getUserPolicyConstraints, getApdPolicyDetails, getUrl)
        .onSuccess(success -> {
          if (getUserPolicyConstraints.result() != null) {
            JsonObject details = new JsonObject();
            details.mergeIn(getUserPolicyConstraints.result());
            details.put(STATUS, SUCCESS);
            details.put(CAT_ID, itemId);
            details.put(URL, getUrl.result());
            p.complete(details);
          } else if (getApdPolicyDetails.result() != null) {
            JsonObject apdDetails = getApdPolicyDetails.result();
            JsonObject apdContext = new JsonObject();

            /*
             * itemType is in uppercase when it comes from the CatalogueClient, making it lowercase
             * explicitly since in the token request, policy listing it's lowercase
             */
            apdContext.put(CALL_APD_APDID, apdDetails.getString(APD_ID))
                .put(CALL_APD_USERID, userId.toString()).put(CALL_APD_ITEM_ID, itemId)
                .put(CALL_APD_ITEM_TYPE, itemType.toLowerCase())
                .put(CALL_APD_RES_SER_URL, getUrl.result())
                .put(CALL_APD_USERCLASS, apdDetails.getString(USER_CLASS))
                .put(CALL_APD_OWNERID, resDetails.get(itemId).getOwnerId().toString())
                .put(CALL_APD_CONTEXT, context)
                .put(CALL_APD_CONSTRAINTS, apdDetails.getJsonObject(CONSTRAINTS));

            apdService.callApd(apdContext, p);
          } else {
            Response r = new ResponseBuilder().status(403).type(URN_INVALID_INPUT)
                .title(INVALID_POLICY).detail(POLICY_NOT_FOUND).build();
            p.fail(new ComposeException(r));
          }
        }).onFailure(failureHandler -> {
          // check if compose Exception, p.fail(composeExp)

          LOGGER.error("failed verifyConsumerPolicy: " + failureHandler.getLocalizedMessage());
          if (failureHandler instanceof ComposeException)
            p.fail(failureHandler);
          else
            p.fail(INTERNALERROR);
        });
    return p.future();
  }

  // change email hash parameter instead of item id for provider flow
  Future<JsonObject> verifyProviderPolicy(
      UUID userId, String itemId, String email_hash, String itemType, boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

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
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_USER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(catServerOptions.getString("catURL")))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getResOwner.compose(
              ar -> {
                if (!getResOwner.result().equals(userId)) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NOT_RES_OWNER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
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
            {
             return   pool.withConnection(
                        conn -> {
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      success.getString(OWNER_ID),
                                      success.getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(NO_ADMIN_POLICY)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return Future.succeededFuture(obj);
                        });});

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
              if (failureHandler instanceof ComposeException) {
                p.fail(failureHandler);
              } else p.fail(INTERNALERROR);
            });

    return p.future();
  }

  Future<JsonObject> verifyDelegatePolicy(
      UUID userId,
      String itemId,
      String email_hash,
      String itemType,
      Map<String, ResourceObj> resDetails,
      boolean isCatalogue) {
    Promise<JsonObject> p = Promise.promise();

    Future<UUID> getOwner;
    if (isCatalogue)
      getOwner =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_RES_OWNER)
                      .execute(Tuple.of(email_hash))
                      .map(
                          rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null));
    else getOwner = Future.succeededFuture(resDetails.get(itemId).getOwnerId());

    Future<JsonObject> getResSerOwner;
    if (isCatalogue) {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_USER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER)
                            .execute(Tuple.of(catServerOptions.getString("catURL")))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    } else {
      getResSerOwner =
          getOwner.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(NO_RES_SERVER)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SERVER_OWNER_ID)
                            .execute(Tuple.of(resDetails.get(itemId).getResServerID()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });
    }

    Future<UUID> checkDelegation =
        getResSerOwner.compose(
            ar -> {
              if (ar == null) {
                Response r =
                    new ResponseBuilder()
                        .status(403)
                        .type(URN_INVALID_INPUT)
                        .title(INVALID_POLICY)
                        .detail(UNAUTHORIZED_DELEGATE)
                        .build();
                return Future.failedFuture(new ComposeException(r));
              }
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
    Future<JsonObject> checkResGrpPolicy;
    Future<JsonObject> checkResPolicy;
    if (isCatalogue) {
      checkPolicy =
          checkDelegation.compose(
              obj -> {
                if (obj == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return Future.succeededFuture(new JsonObject());
              });
    } else {

      checkResGrpPolicy =
          checkDelegation.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(CHECK_POLICY)
                            .execute(
                                Tuple.of(
                                    userId,
                                    getOwner.result(),
                                    resDetails.get(itemId).getResGrpId(),
                                    status.ACTIVE.toString()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });

      checkResPolicy =
          checkDelegation.compose(
              ar -> {
                if (ar == null) {
                  Response r =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_INVALID_INPUT)
                          .title(INVALID_POLICY)
                          .detail(UNAUTHORIZED_DELEGATE)
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                }
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(CHECK_POLICY)
                            .execute(
                                Tuple.of(
                                    userId,
                                    getOwner.result(),
                                    resDetails.get(itemId).getId(),
                                    status.ACTIVE.toString()))
                            .map(
                                rows ->
                                    rows.rowCount() > 0 ? rows.iterator().next().toJson() : null));
              });

      checkPolicy =
          CompositeFuture.all(checkResGrpPolicy, checkResPolicy)
              .compose(
                  ar -> {
                    if (itemType.equals(itemTypes.RESOURCE_GROUP.toString()))
                      return checkResGrpPolicy;
                    else {
                      if (checkResPolicy.result() == null) return checkResGrpPolicy;
                      return checkResPolicy;
                    }
                  });
    }

    Future<UUID> checkAdminPolicy =
        checkPolicy.compose(
            success ->
                pool.withConnection(
                        conn -> {
                          if (checkPolicy.result() == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(UNAUTHORIZED_DELEGATE)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return conn.preparedQuery(CHECK_ADMIN_POLICY)
                              .execute(
                                  Tuple.of(
                                      userId,
                                      getResSerOwner.result().getString(OWNER_ID),
                                      getResSerOwner.result().getString(ID),
                                      itemTypes.RESOURCE_SERVER.toString(),
                                      status.ACTIVE.toString()));
                        })
                    .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getUUID(ID) : null)
                    .compose(
                        obj -> {
                          if (obj == null) {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(NO_ADMIN_POLICY)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                          return Future.succeededFuture(obj);
                        }));

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
              LOGGER.error("failed verifyDelegatePolicy ");
              if (failureHandler instanceof ComposeException) {
                ComposeException exp = (ComposeException) failureHandler;
                p.fail(failureHandler);
              } else p.fail(INTERNALERROR);
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
    /* context will be empty JSON object if not set by user; see RequestToken */
    JsonObject context = request.getJsonObject(CONTEXT);

    boolean isCatalogue = false;
    // verify policy does not expect the resServer itemType
    if (itemType.equals(itemTypes.RESOURCE_SERVER.toString())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(INVALID_POLICY)
              .detail(INCORRECT_ITEM_TYPE)
              .build();
      handler.handle(Future.failedFuture(new ComposeException(r)));
      return this;
    }

    if (role.equals(roles.ADMIN.toString())) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(INVALID_POLICY)
              .detail(INVALID_ROLE)
              .build();
      handler.handle(Future.failedFuture(new ComposeException(r)));
      return this;
    }

    String[] itemSplit = itemId.split("/");

    if (itemSplit.length < 4 && (itemType.equals(itemTypes.RESOURCE.toString())
        || itemType.equals(itemTypes.RESOURCE_GROUP.toString()))) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(INVALID_POLICY)
                .detail(INCORRECT_ITEM_ID)
                .build();
        handler.handle(Future.failedFuture(new ComposeException(r)));
        return this;
    }
    
    String emailHash = itemSplit[0] + "/" + itemSplit[1];
    Future<String> getRoles =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_FROM_ROLES_TABLE)
                    .execute(Tuple.of(userId, roles.valueOf(role), status.APPROVED))
                    .compose(
                        ar -> {
                          if (ar.rowCount() > 0) {
                            return Future.succeededFuture(ar.iterator().next().getString(ROLE));
                          } else {
                            Response r =
                                new ResponseBuilder()
                                    .status(403)
                                    .type(URN_INVALID_INPUT)
                                    .title(INVALID_POLICY)
                                    .detail(INVALID_ROLE)
                                    .build();
                            return Future.failedFuture(new ComposeException(r));
                          }
                        }));

    if (itemSplit.length == 5
        && itemSplit[2].equals(catServerOptions.getString("catURL"))
        && (itemSplit[3] + "/" + itemSplit[4]).equals(catServerOptions.getString("catItem"))) {

      isCatalogue = true;
    }

    Future<Map<String, ResourceObj>> reqItemDetail;
    if (!isCatalogue) {

      if (itemType.equals(itemTypes.RESOURCE_GROUP.toString()) && itemId.split("/").length != 4) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(INVALID_POLICY)
                .detail(INCORRECT_ITEM_TYPE)
                .build();
        handler.handle(Future.failedFuture(new ComposeException(r)));
        return this;
      }

      if (itemType.equals(itemTypes.RESOURCE.toString()) && itemId.split("/").length <= 4) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(INVALID_POLICY)
                .detail(INCORRECT_ITEM_TYPE)
                .build();
        handler.handle(Future.failedFuture(new ComposeException(r)));
        return this;
      }

      // create map of item, use catalogue client - checkReqItems to check and fetch item
      Map<String, List<String>> catItem = new HashMap<>();
      if (itemType.equals(itemTypes.RESOURCE.toString())) {

        ArrayList<String> list = new ArrayList<>();
        list.add(itemId);
        catItem.put(RES, list);
      }
      if (itemType.equals(itemTypes.RESOURCE_GROUP.toString())) {
        ArrayList<String> list = new ArrayList<>();
        list.add(itemId);
        catItem.put(RES_GRP, list);
      }
      reqItemDetail = catalogueClient.checkReqItems(catItem);
      // trim itemid for resitem type

    } else {
      reqItemDetail = Future.succeededFuture(new HashMap<>());
    }

    boolean finalIsCatalogue = isCatalogue;
    String finalItem = itemId;
    Future<JsonObject> verifyRolePolicy =
        CompositeFuture.all(getRoles, reqItemDetail)
            .compose(
                success -> {
                  Future<JsonObject> response;
                  switch (getRoles.result()) {
                    case CONSUMER_ROLE:
                      {
                        response =
                            verifyConsumerPolicy(
                                userId, finalItem, itemType, context, reqItemDetail.result());
                        break;
                      }
                    case PROVIDER_ROLE:
                      {
                        response =
                            verifyProviderPolicy(
                                userId, finalItem, emailHash, itemType, finalIsCatalogue);
                        break;
                      }
                    case DELEGATE_ROLE:
                      {
                        response =
                            verifyDelegatePolicy(
                                userId,
                                finalItem,
                                emailHash,
                                itemType,
                                reqItemDetail.result(),
                                finalIsCatalogue);
                        break;
                      }
                    default:
                      {
                        response = Future.failedFuture(INTERNALERROR);
                      }
                  }
                  return response;
                });

    verifyRolePolicy.onSuccess(
        s -> {
          handler.handle(Future.succeededFuture(s));
        });

    verifyRolePolicy.onFailure(
        e -> {
          LOGGER.error("verifyRolePolicy failed" + e.getMessage());
          if (e instanceof ComposeException) {
            handler.handle(Future.failedFuture(e));
          } else handler.handle(Future.failedFuture(INTERNALERROR));
        });

    return this;
  }

  @Override
  public PolicyService checkAuthPolicy(String userId, Handler<AsyncResult<Void>> handler) {
    Future<Boolean> policyExists = createPolicy.checkAuthPolicy(userId);
    policyExists
        .onSuccess(succ -> handler.handle(Future.succeededFuture()))
        .onFailure(
            fail -> {
              if (fail instanceof ComposeException) {
                ComposeException e = (ComposeException) fail;
                Response resp = e.getResponse();
                resp.setDetail(NO_AUTH_ADMIN_POLICY);
                handler.handle(Future.failedFuture(new ComposeException(resp)));
                return;
              }
              handler.handle(Future.failedFuture(fail));
            });
    return this;
    }

  /** {@inheritDoc} */
  @Override
  public PolicyService createPolicyNotification(
      List<CreatePolicyNotification> request, User user, Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    emailInfo.setConsumerId(UUID.fromString(user.getUserId()));
    emailInfo.setRequest(request);

    List<Roles> roles = user.getRoles();
    JsonObject userJson = user.toJson();
    userJson.remove("keycloakId");
    userJson.remove("roles");

    if (!roles.contains(Roles.CONSUMER)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(INVALID_ROLE)
              .detail(INVALID_ROLE)
              .status(403)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<CreatePolicyNotification> duplicates = request.stream()
        .collect(
            Collectors.groupingBy(p -> p.getItemId() + "-" + p.getItemType(), Collectors.toList()))
        .values().stream().filter(i -> i.size() > 1).flatMap(j -> j.stream())
        .collect(Collectors.toList());

    if (duplicates.size() > 0) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(SUCC_NOTIF_REQ)
          .detail(DUPLICATE).status(400).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resGrpIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject
                        .getItemType()
                        .toUpperCase()
                        .equals(itemTypes.RESOURCE_GROUP.toString()))
            .map(CreatePolicyNotification::getItemId)
            .collect(Collectors.toList());

    if (!resGrpIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length == 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<String> resIds =
        request.stream()
            .filter(
                tagObject ->
                    tagObject.getItemType().toUpperCase().equals(itemTypes.RESOURCE.toString()))
            .map(CreatePolicyNotification::getItemId)
            .collect(Collectors.toList());

    if (!resIds.stream().allMatch(itemTypeCheck -> itemTypeCheck.split("/").length > 4)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INCORRECT_ITEM_TYPE)
              .detail(INCORRECT_ITEM_TYPE)
              .status(400)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Map<String, List<String>> catItem = new HashMap<>();

    if (resGrpIds.size() > 0) {
      catItem.put(RES_GRP, resGrpIds);
    }
    if (resIds.size() > 0) {
      catItem.put(RES, resIds);
    }

    Future<Map<String, ResourceObj>> reqCatItem = catalogueClient.checkReqItems(catItem);

    reqCatItem.onComplete(
        dbHandler -> {
          if (dbHandler.failed()) {
            LOGGER.error("reqCatItem failed " + dbHandler.cause());
            Response resp =
                new ResponseBuilder()
                    .status(400)
                    .type(URN_INVALID_INPUT)
                    .title(ITEMNOTFOUND)
                    .detail(ITEMNOTFOUND)
                    .build();
            handler.handle(Future.succeededFuture(resp.toJson()));
            return;
          }

          if (dbHandler.succeeded()) {
            emailInfo.setItemDetails(reqCatItem.result());

            Future<Map<UUID,List<UUID>>> providerToAuthDelegate = providerToAuthDelegate(reqCatItem.result());
            Future<List<Tuple>> tuples = mapTupleCreate(request, reqCatItem.result(), user);
            Future<List<Tuple>> checkDuplicate = checkDuplication(tuples.result());

            CompositeFuture.all(tuples, checkDuplicate,providerToAuthDelegate)
                .onComplete(
                    resHandler -> {
                      if (resHandler.failed()) {
                        String msg = URN_INVALID_INPUT.toString();
                        int status = 400;
                        if (checkDuplicate.failed()) {
                          msg = URN_ALREADY_EXISTS.toString();
                          status = 409;
                        }

                        LOGGER.error(LOG_DB_ERROR + resHandler.cause().getLocalizedMessage());
                        Response resp =
                            new ResponseBuilder()
                                .status(status)
                                .type(msg)
                                .title(resHandler.cause().getLocalizedMessage())
                                .detail(resHandler.cause().getLocalizedMessage())
                                .build();
                        handler.handle(Future.succeededFuture(resp.toJson()));
                        return;
                      }

                      if (resHandler.succeeded()) {
                        emailInfo.setProviderIdToAuthDelegateId(providerToAuthDelegate.result());
                        pool.withTransaction(
                            conn -> conn.preparedQuery(CREATE_NOTIFI_POLICY_REQUEST)
                                .executeBatch(tuples.result()).compose(rows -> {
                                  JsonArray resp = new JsonArray();
                                  while (rows != null) {
                                    rows.iterator().forEachRemaining(row -> {
                                      resp.add(row.toJson());
                                    });
                                    rows = rows.next();
                                  }

                                  List<String> ids = new ArrayList<>();
                                  ids.add(user.getUserId());

                                  Set<String> providerToAuthDelegateIds = new HashSet<>();
                                  for(List<UUID> uuids : providerToAuthDelegate.result().values()){
                                    providerToAuthDelegateIds.addAll(
                                        uuids.stream().map(Functions.toStringFunction())
                                            .collect(Collectors.toList()));
                                  }
                                  List<String> ownerIds = dbHandler.result().values().stream()
                                      .map(resObj -> resObj.getOwnerId().toString())
                                      .collect(Collectors.toList());
                                  ids.addAll(ownerIds);
                                  ids.addAll(providerToAuthDelegateIds);

                                  Promise<JsonArray> response = Promise.promise();
                                  registrationService.getUserDetails(ids, userHandler -> {
                                    if (userHandler.failed()) {
                                      response.fail(
                                          "Fail: Registration failure; " + userHandler.cause());
                                    }

                                    if (userHandler.succeeded()) {
                                      Map<String, JsonObject> userInfo =
                                          jsonObjectToMap.apply(userHandler.result());
                                      emailInfo.setUserInfo(userInfo);
                                      JsonObject userJson1 = userInfo.get(user.getUserId());
                                      userJson1.put(ID, user.getUserId());

                                      JsonArray results = new JsonArray();
                                      for (int i = 0; i < request.size(); i++) {
                                        JsonObject requestJson = request.get(i).toJson();
                                        requestJson.put(STATUS,
                                            NotifRequestStatus.PENDING.name().toLowerCase());

                                        String ownerId = ownerIds.get(i);
                                        JsonObject ownerInfo = userInfo.get(ownerId);
                                        ownerInfo.put(ID, ownerId);
                                        JsonObject eachJson = resp.getJsonObject(i).copy()
                                            .mergeIn(requestJson).put(USER_DETAILS, userJson1)
                                            .put(OWNER_DETAILS, ownerInfo);
                                        results.add(eachJson);
                                      }
                                      response.complete(results);
                                    }
                                  });
                                  return response.future();
                                }))
                            .onSuccess(created -> {

                              LOGGER.info("Success: {}; Id: {}", SUCC_NOTIF_REQ, created);
                              Response res = new Response.ResponseBuilder().type(URN_SUCCESS)
                                  .title(SUCC_TITLE_POLICY_READ).status(200).arrayResults(created)
                                  .build();
                              //emailService(emailInfo)
                              handler.handle(Future.succeededFuture(res.toJson()));
                            }).onFailure(fail -> {
                              LOGGER.error(LOG_DB_ERROR + fail.getLocalizedMessage());
                              handler.handle(Future.failedFuture("Internal error"));
                            });
                      }
                    });
          }
        });
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService listPolicyNotification(
      User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    boolean isDelegate = !data.isEmpty();
    List<Roles> roles = user.getRoles();

    if (!(isDelegate || roles.contains(Roles.PROVIDER) || roles.contains(Roles.CONSUMER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String query;
    Tuple queryTuple;

    if (isDelegate) {
      UUID providerId = UUID.fromString(data.getString("providerId"));
      query = SELECT_PROVIDER_NOTIF_REQ;
      queryTuple = Tuple.of(providerId,status.WITHDRAWN);
    } else if (roles.contains(Roles.PROVIDER)) {
      query = SELECT_PROVIDER_NOTIF_REQ;
      queryTuple = Tuple.of(user.getUserId(),status.WITHDRAWN);
    } else {
      query = SELECT_CONSUM_NOTIF_REQ;
      queryTuple = Tuple.of(user.getUserId(),status.WITHDRAWN);
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> notifData =
        pool.withTransaction(
            conn ->
                conn.query(SET_INTERVALSTYLE)
                    .execute()
                    .flatMap(
                        result ->
                            conn.preparedQuery(query)
                                .collecting(collect)
                                .execute(queryTuple)
                                .map(res -> res.value())));

    Future<JsonArray> itemNames =
        notifData.compose(
            result -> {
              Promise<JsonArray> promise = Promise.promise();

              Collector<Row, ?, Map<UUID, String>> collectItemName =
                  Collectors.toMap(row -> row.getUUID(ID), row -> row.getString(URL));

              Set<UUID> itemIds =
                  result.stream()
                      .map(each -> UUID.fromString(each.getString(ITEMID)))
                      .collect(Collectors.toSet());

              Future<Map<UUID, String>> getNames =
                  CompositeFuture.all(catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE_GROUP),
                      catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE)).compose(res -> {
                        Map<UUID, String> resGroups = res.resultAt(0);
                        Map<UUID, String> resItems = res.resultAt(1);
                        /* merging the two maps into resGroups */
                        resGroups.putAll(resItems);
                        return Future.succeededFuture(resGroups);
                      });

              getNames
                  .onFailure(
                      failureHandler -> {
                        promise.fail(failureHandler.getLocalizedMessage());
                      })
                  .onSuccess(
                      nameMapper -> {
                        JsonArray resArr = new JsonArray();
                        result.forEach(
                            each -> {
                              UUID itemId = UUID.fromString(each.getString(ITEMID));
                              each.put(ITEMID, nameMapper.get(itemId));
                              resArr.add(each);
                            });
                        promise.complete(resArr);
                      });

              return promise.future();
            });

    Future<JsonObject> userInfo =
        itemNames.compose(
            result -> {
              Set<String> ids = new HashSet<String>();
              result.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;
                    ids.add(each.getString(OWNER_ID));
                    ids.add(each.getString(USER_ID));
                  });

              Promise<JsonObject> userDetails = Promise.promise();
              registrationService.getUserDetails(new ArrayList<String>(ids), userDetails);
              return userDetails.future();
            });

    userInfo
        .onSuccess(
            result -> {
              JsonArray notifRequest = itemNames.result();
              JsonArray response = new JsonArray();
              Map<String, JsonObject> details = jsonObjectToMap.apply(result);

              notifRequest.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;

                    String userId = (String) each.remove(USER_ID);
                    String ownerId = (String) each.remove(OWNER_ID);

                    JsonObject eachDetails =
                        each.copy()
                            .put(USER_DETAILS, details.get(userId).put(ID, userId))
                            .put(OWNER_DETAILS, details.get(ownerId).put(ID, ownerId));

                    response.add(eachDetails);
                  });

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_LIST_NOTIF_REQ)
                      .arrayResults(response)
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              LOGGER.error(ERR_LIST_NOTIF + "; {}", e.getMessage());
              handler.handle(Future.failedFuture(INTERNALERROR));
            });

    return this;
  }

  /** {@inheritDoc} */
  @Override
  public PolicyService updatePolicyNotification(List<UpdatePolicyNotification> request,
      User user, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    boolean isDelegate = !data.isEmpty();
    List<Roles> roles = user.getRoles();
    UUID ownerId = isDelegate ? UUID.fromString(data.getString("providerId"))
        : UUID.fromString(user.getUserId());

    if (!((isDelegate && roles.contains(Roles.DELEGATE)) || roles.contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES).status(401).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<UUID> requestIds = request.stream().map(each -> UUID.fromString(each.getRequestId()))
        .collect(Collectors.toList());

    Set<UUID> uniqueRequestIds = new HashSet<UUID>(requestIds);
    if (requestIds.size() != uniqueRequestIds.size()) {
      LOGGER.error("Fail: {}", DUPLICATE);
      Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(SUCC_NOTIF_REQ)
          .detail(DUPLICATE).status(400).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Collector<Row, ?, List<JsonObject>> notifRequestCollect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Map<UUID, JsonObject> requestMap = request.stream().collect(
        Collectors.toMap(key -> UUID.fromString(key.getRequestId()), value -> value.toJson()));

    Tuple tup = Tuple.of(requestIds.toArray(UUID[]::new), ownerId);
    Future<List<JsonObject>> policyRequestData =
        pool.withTransaction(conn -> conn.preparedQuery(SET_INTERVALSTYLE).execute()
            .flatMap(result -> conn.preparedQuery(SEL_NOTIF_REQ_ID).collecting(notifRequestCollect)
                .execute(tup).map(res -> res.value())));

    Future<JsonArray> createPolicyArray = policyRequestData.compose(dbHandler -> {

      if (dbHandler.size() != request.size()) {
        LOGGER.debug("Info: {}", REQ_ID_ALREADY_NOT_EXISTS);

        Response resp = new ResponseBuilder().status(404).type(URN_INVALID_INPUT)
            .title(SUCC_NOTIF_REQ).detail(REQ_ID_ALREADY_NOT_EXISTS).build();
        return Future.failedFuture(new ComposeException(resp));
      }

      /*
       * We can do this in the previous query, but since there is a separate response for 'request
       * ID already processed', we do it here
       */
      Boolean nonPendingReqIds = dbHandler.stream()
          .anyMatch(obj -> !obj.getString("status").equals(NotifRequestStatus.PENDING.name()));

      if (nonPendingReqIds) {
        return Future.failedFuture(
            new ComposeException(400, URN_INVALID_INPUT, SUCC_NOTIF_REQ, REQ_ID_ALREADY_PROCESSED));
      }

      List<JsonObject> notifReqlist = dbHandler;
      JsonArray createPolicyArr = new JsonArray();

      notifReqlist.forEach(each -> {
        UUID requestId = UUID.fromString(each.getString(ID));
        JsonObject requestJson = requestMap.get(requestId);
        if (requestJson != null) {
          JsonObject temp = each.copy().mergeIn(requestJson, Boolean.TRUE);
          createPolicyArr.add(temp);
        }
      });

      return Future.succeededFuture(createPolicyArr);
    });

    Future<Map<UUID, String>> getItemIdName = createPolicyArray.compose(createPolicyArr -> {
      Set<UUID> itemIds = createPolicyArr.stream().map(JsonObject.class::cast)
          .map(each -> UUID.fromString(each.getString(ITEMID))).collect(Collectors.toSet());

      return CompositeFuture.all(catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE_GROUP),
          catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE)).compose(res -> {
            Map<UUID, String> resGroups = res.resultAt(0);
            Map<UUID, String> resItems = res.resultAt(1);
            /* merging the two maps into resGroups */
            resGroups.putAll(resItems);
            return Future.succeededFuture(resGroups);
          });
    });

    Promise<JsonArray> approvedRequestArray = Promise.promise();
    Promise<JsonArray> rejectedRequestArray = Promise.promise();
    Promise<List<Tuple>> approvedRequestTupList = Promise.promise();
    Promise<List<Tuple>> rejectedRequestTupList = Promise.promise();

    Future<Void> approvedRejectedQueryData = getItemIdName.compose(idMap -> {

      LocalDateTime start = LocalDateTime.now();
      List<Tuple> selectPolicy = new ArrayList<>();
      JsonArray resArr = new JsonArray();

      JsonArray approvedReq = createPolicyArray.result().stream().map(JsonObject.class::cast)
          .filter(each -> each.getString(STATUS).equals(NotifRequestStatus.APPROVED.name()))
          .map(each -> {
            String expiry = each.getString("expiryDuration");
            String itemId = each.getString(ITEMID);

            org.joda.time.Interval interval = org.joda.time.Interval.parse(start + "/" + expiry);
            each.put(EXPIRYTIME, interval.getEnd().toString());
            each.put(ITEMID, idMap.get(UUID.fromString(itemId)));
            selectPolicy.add(Tuple.of(each.getString(USERID), itemId, each.getString(OWNERID),
                each.getString(ID)));

            return each;
          }).collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add));

      approvedRequestArray.complete(approvedReq);
      approvedRequestTupList.complete(selectPolicy);

      List<Tuple> updateRejectedReq = createPolicyArray.result().stream()
          .map(JsonObject.class::cast)
          .filter(each -> each.getString(STATUS).equals(NotifRequestStatus.REJECTED.name()))
          .map(each -> {
            UUID requestId = UUID.fromString(each.getString("requestId"));
            String status = each.getString(STATUS);

            String itemId = each.getString(ITEMID);
            each.put(ITEMID, idMap.get(UUID.fromString(itemId)));
            each.put(STATUS, status.toLowerCase());
            resArr.add(each);
            return Tuple.of(requestId, status);
          }).collect(Collectors.toList());

      rejectedRequestTupList.complete(updateRejectedReq);
      rejectedRequestArray.complete(resArr);
      return Future.succeededFuture();
    });

    Future<JsonObject> userDetails = approvedRejectedQueryData.compose(res -> {

      List<String> consumerIds = createPolicyArray.result().stream().map(JsonObject.class::cast)
          .map(each -> each.getString(USERID)).collect(Collectors.toList());

      List<String> ids = new ArrayList<String>();
      ids.add(ownerId.toString());
      ids.addAll(consumerIds);

      Promise<JsonObject> p = Promise.promise();
      registrationService.getUserDetails(ids, p);
      return p.future();
    });

    /*
     * We create 2 functional interfaces, one for updating approved requests, the other for updating
     * rejected requests. The SqlConnection passed is created using withTransaction so that the
     * rollback (if it occurs) will affect both approve and reject operations. We use the functional
     * interfaces since approving and rejecting requests have different queries, and we need to be
     * able to rollback both queries in case any one fails. May be possible to use
     * pool.getConnection(), but not sure if a rollback is triggered when a future fails somewhere.
     */
    Function<SqlConnection, Future<Void>> updateApprovedRequests = (SqlConnection conn) -> {
      JsonArray approvedReq = approvedRequestArray.future().result();

      if (approvedReq.size() == 0) {
        return Future.succeededFuture();
      }

      List<UpdatePolicyNotification> updateReq =
          UpdatePolicyNotification.jsonArrayToList(approvedReq);

      Future<List<Tuple>> updateApprovedTup = mapTupleUpdate(updateReq);

      List<CreatePolicyRequest> createPolicyReqArray =
          CreatePolicyRequest.jsonArrayToList(approvedRequestArray.future().result());

      Collector<Row, ?, List<Tuple>> policyCollector = Collectors.mapping(
          row -> Tuple.of(row.getUUID("requestId"), row.getUUID("policyId")), Collectors.toList());

      return updateApprovedTup.compose(mapper -> conn.preparedQuery(UPDATE_NOTIF_REQ_APPROVED)
          .executeBatch(mapper).compose(updateSuccHandler -> {
            Promise<JsonObject> createPolicyRes = Promise.promise();
            createPolicy(createPolicyReqArray, user, data, createPolicyRes);
            return createPolicyRes.future();
          }).compose(createdHandler -> {
            JsonObject result = createdHandler;
            if (URN_SUCCESS.toString().equalsIgnoreCase(result.getString(TYPE))) {
              /* NOTE: If a failure occurs here, the policy creation cannot be rolled back */
              return conn.preparedQuery(SEL_NOTIF_POLICY_ID).collecting(policyCollector)
                  .executeBatch(approvedRequestTupList.future().result())
                  .flatMap(insert -> conn.preparedQuery(INSERT_NOTIF_APPROVED_ID)
                      .executeBatch(insert.value()).mapEmpty());
            } else {
              return Future.failedFuture(new ComposeException(400, URN_INVALID_INPUT,
                  SUCC_NOTIF_REQ, result.getString(TITLE)));
            }
          }));
    };

    Function<SqlConnection, Future<Void>> updateRejectedRequests = (SqlConnection conn) -> {
      List<Tuple> updateRejectedTup = rejectedRequestTupList.future().result();

      if (updateRejectedTup.size() == 0) {
        return Future.succeededFuture();
      }

      return conn.preparedQuery(UPDATE_NOTIF_REQ_REJECTED).executeBatch(updateRejectedTup)
          .mapEmpty();
    };

    /*
     * Open the transaction using withTransaction and pass the conn object to the update approved
     * and rejected request functions
     */
    Future<Void> updatedRequests = userDetails.compose(x -> {
      return pool.withTransaction(conn -> updateRejectedRequests.apply(conn)
          .compose(res -> updateApprovedRequests.apply(conn)));
    });

    updatedRequests.compose(result -> {
      Map<String, JsonObject> userInfo = jsonObjectToMap.apply(userDetails.result());

      JsonObject ownerJson = userInfo.get(ownerId.toString()).put(ID, ownerId.toString());

      JsonArray results = new JsonArray();
      JsonArray resArr = rejectedRequestArray.future().result();
      resArr.addAll(approvedRequestArray.future().result());
      for (int i = 0; i < resArr.size(); i++) {
        JsonObject requestJson = resArr.getJsonObject(i);
        LOGGER.info("Updated status of request ID {} to {}", requestJson.getString(ID),
            requestJson.getString(STATUS).toUpperCase());

        requestJson.remove(EXPIRYTIME);
        String consumerId = (String) requestJson.remove(USERID);
        requestJson.remove(OWNERID);
        requestJson.remove(ID);

        requestJson.put(STATUS, requestJson.getString(STATUS).toLowerCase());
        requestJson.put(ITEMTYPE, requestJson.getString(ITEMTYPE).toLowerCase());
        requestJson.put(USER_DETAILS, userInfo.get(consumerId).put(ID, consumerId));
        requestJson.put(OWNER_DETAILS, ownerJson);

        results.add(requestJson.copy());
      }
      return Future.succeededFuture(results);
    }).onSuccess(results -> {
      LOGGER.info("Success: {}; {}", SUCC_NOTIF_REQ, SUCC_UPDATE_NOTIF_REQ);
      Response res = new Response.ResponseBuilder().type(URN_SUCCESS).title(SUCC_UPDATE_NOTIF_REQ)
          .status(200).arrayResults(results).build();
      handler.handle(Future.succeededFuture(res.toJson()));
      return;
    }).onFailure(obj -> {
      if (obj instanceof ComposeException) {
        ComposeException e = (ComposeException) obj;
        handler.handle(Future.succeededFuture(e.getResponse().toJson()));
        return;
      }
      LOGGER.error(obj.getMessage());
      handler.handle(Future.failedFuture(INTERNALERROR));
    });
    return this;
  }

  @Override
  public PolicyService deletePolicyNotification(List<DeletePolicyNotificationRequest> request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (!user.getRoles().contains(Roles.CONSUMER)) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_CONSUMER_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
      }

    Tuple queryTup;
    List<UUID> ids =
        request.stream().map(obj -> UUID.fromString(obj.getId())).collect(Collectors.toList());

    queryTup =
            Tuple.of(UUID.fromString(user.getUserId())).addArrayOfUUID(ids.toArray(UUID[]::new));

    Collector<Row, ?, List<JsonObject>>collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

      Future<List<JsonObject>> getIds =
          pool.withConnection(
              conn ->
                  conn.preparedQuery(GET_NOTIFICATIONS_BY_ID_CONSUMER)
                      .collecting(collect)
                      .execute(queryTup)
                      .map(res -> res.value()));

      Future<Void> validate = getIds.compose(objs ->
      {
        List<UUID> validIds = objs.stream().map(ar -> UUID.fromString(ar.getString("requestId")))
            .collect(Collectors.toList());

        if(validIds.size() < ids.size())
        {
         ids.removeAll(validIds);
          Response r =
              new ResponseBuilder()
                  .type(URN_INVALID_INPUT)
                  .title(REQ_ID_ALREADY_NOT_EXISTS)
                  .detail(ids.toString())
                  .status(400)
                  .build();
          return Future.failedFuture(new ComposeException(r));
        }
        return Future.succeededFuture();
      });

    Future<JsonArray> itemNames =
        validate.compose(
            result -> {
              Promise<JsonArray> promise = Promise.promise();
              Set<UUID> itemIds = new HashSet<>();
              getIds.result().forEach(
                  each ->
                  {
                    itemIds.add(UUID.fromString(each.getString(ITEMID)));
                  }
              );
              Future<Map<UUID, String>> getNames =
                  CompositeFuture.all(catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE_GROUP),
                      catalogueClient.getCatIds(itemIds, itemTypes.RESOURCE)).compose(res -> {
                    Map<UUID, String> resGroups = res.resultAt(0);
                    Map<UUID, String> resItems = res.resultAt(1);
                    /* merging the two maps into resGroups */
                    resGroups.putAll(resItems);
                    return Future.succeededFuture(resGroups);
                  });

              getNames
                  .onFailure(
                      failureHandler -> {
                        promise.fail(failureHandler.getLocalizedMessage());
                      })
                  .onSuccess(
                      nameMapper -> {
                        JsonArray resArr = new JsonArray();
                        getIds.result().forEach(
                            each -> {
                              UUID itemId = UUID.fromString(each.getString(ITEMID));
                              each.put(ITEMID, nameMapper.get(itemId));
                              resArr.add(each);
                            });
                        promise.complete(resArr);
                      });

              return promise.future();
            });

    Future<JsonObject> userInfo =
        itemNames.compose(
            result -> {
              Set<String> itemIds = new HashSet<String>();
              result.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;
                    itemIds.add(each.getString(OWNER_ID));
                    itemIds.add(each.getString(USER_ID));
                  });

              Promise<JsonObject> userDetails = Promise.promise();
              registrationService.getUserDetails(new ArrayList<String>(itemIds), userDetails);
              return userDetails.future();
            });

    Future<RowSet<Row>> deleteNotifs = userInfo.compose(ar ->
        pool.withConnection(conn -> conn.preparedQuery(DELETE_NOTIFICATIONS).execute(queryTup)));

    deleteNotifs
        .onSuccess(
            result -> {
              JsonArray notifRequest = itemNames.result();
              JsonArray response = new JsonArray();
              Map<String, JsonObject> details = jsonObjectToMap.apply(userInfo.result());

              notifRequest.forEach(
                  obj -> {
                    JsonObject each = (JsonObject) obj;
                    String userId = (String) each.remove(USER_ID);
                    String ownerId = (String) each.remove(OWNER_ID);

                    JsonObject eachDetails =
                        each.copy()
                            .put(USER_DETAILS, details.get(userId).put(ID, userId))
                            .put(OWNER_DETAILS, details.get(ownerId).put(ID, ownerId))
                            .put(STATUS,status.WITHDRAWN.toString().toLowerCase());

                    response.add(eachDetails);
                  });

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(DELETE_NOTIF_REQ)
                      .arrayResults(response)
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              LOGGER.error(ERR_DELETE_NOTIF + "; {}", e.getMessage());
              if (e instanceof ComposeException) {
                ComposeException exp = (ComposeException) e;
                handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
                return;
              }
              handler.handle(Future.failedFuture(INTERNALERROR));
            });
    return this;
    }

  @Override
  public PolicyService listDelegation(
      User user, JsonObject authDelegateDetails, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate
        || user.getRoles().contains(Roles.PROVIDER)
        || user.getRoles().contains(Roles.DELEGATE))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String query;
    Tuple queryTup;

    /* get all delegations EXCEPT auth server delegations */
    if (isAuthDelegate) {
      UUID providerUserId = UUID.fromString(authDelegateDetails.getString("providerId"));
      query = LIST_DELEGATE_AUTH_DELEGATE;
      queryTup = Tuple.of(providerUserId, authOptions.getString("authServerUrl"));
    } else {
      query = LIST_DELEGATE_AS_PROVIDER_DELEGATE;
      queryTup = Tuple.of(UUID.fromString(user.getUserId()));
    }

    Collector<Row, ?, List<JsonObject>> collect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> data =
        pool.withConnection(
            conn ->
                conn.preparedQuery(query)
                    .collecting(collect)
                    .execute(queryTup)
                    .map(res -> res.value()));

    Future<JsonObject> userInfo =
        data.compose(
            result -> {
              Set<String> ss = new HashSet<String>();
              result.forEach(
                  obj -> {
                    ss.add(obj.getString("owner_id"));
                    ss.add(obj.getString("user_id"));
                  });

              Promise<JsonObject> userDetails = Promise.promise();
              registrationService.getUserDetails(new ArrayList<String>(ss), userDetails);
              return userDetails.future();
            });

    userInfo
        .onSuccess(
            results -> {
              List<JsonObject> deleRes = data.result();
              Map<String, JsonObject> details = jsonObjectToMap.apply(results);

              deleRes.forEach(
                  obj -> {
                    JsonObject ownerDet = details.get(obj.getString("owner_id"));
                    ownerDet.put("id", obj.remove("owner_id"));

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
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            e -> {
              LOGGER.error(e.getMessage());
              handler.handle(Future.failedFuture("Internal error"));
            });
    return this;
  }

  @Override
  public PolicyService deleteDelegation(
      List<DeleteDelegationRequest> request,
      User user,
      JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    boolean isAuthDelegate = !authDelegateDetails.isEmpty();

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_DEL_DELEGATE_ROLES)
              .status(401)
              .build();
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
                    ids.stream().filter(id -> !data.containsKey(id)).collect(Collectors.toList());

                return Future.failedFuture(new ComposeException(400, URN_INVALID_INPUT,
                    ERR_TITLE_INVALID_ID, badIds.get(0).toString()));
              }

              if (!isAuthDelegate) {
                return Future.succeededFuture();
              }

              List<UUID> authDelegs =
                  data.entrySet().stream()
                      .filter(obj -> obj.getValue().equals(authOptions.getString("authServerUrl")))
                      .map(obj -> obj.getKey())
                      .collect(Collectors.toList());

              if (!authDelegs.isEmpty()) {
                return Future.failedFuture(new ComposeException(403, URN_INVALID_INPUT,
                    ERR_TITLE_AUTH_DELE_DELETE, authDelegs.get(0).toString()));
              }
              return Future.succeededFuture();
            });

    validate
        .compose(
            i ->
                pool.withTransaction(
                    conn -> conn.preparedQuery(DELETE_DELEGATIONS).execute(queryTup)))
        .onSuccess(
            res -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_DELETE_DELE)
                      .objectResults(new JsonObject())
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(e -> {
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

  // TO-DO change method to accept only ResourceObject instead of resourceID
  /**
   * Create/map objects, fields to List<tuple>.
   *
   * @param request
   * @param objDetails
   * @param user
   * @return List<Tuple>
   */
  Future<List<Tuple>> mapTupleCreate(
      List<CreatePolicyNotification> request, Map<String, ResourceObj> objDetails, User user) {

    Promise<List<Tuple>> promise = Promise.promise();
    List<Tuple> tuples = new ArrayList<>();

    String userId = user.getUserId();
    try {
      for (CreatePolicyNotification each : request) {

        String catId = each.getItemId();
        ResourceObj resource = objDetails.get(catId);
        UUID itemId = resource.getId();
        UUID ownerId = resource.getOwnerId();

        String status = NotifRequestStatus.PENDING.name();
        String itemType = each.getItemType().toUpperCase();

        Duration duration = DatatypeFactory.newInstance().newDuration(each.getExpiryDuration());
        JsonObject constraints = each.getConstraints();

        Interval interval =
            Interval.of(
                duration.getYears(),
                duration.getMonths(),
                duration.getDays(),
                duration.getHours(),
                duration.getMinutes(),
                duration.getSeconds());

        tuples.add(Tuple.of(userId, itemId, itemType, ownerId, status, interval, constraints));
      }
    } catch (DatatypeConfigurationException e) {
      LOGGER.error("Fail: {}; {}", INVALID_TUPLE, e.getLocalizedMessage());
      promise.fail(INVALID_TUPLE);
    }

    promise.complete(tuples);
    return promise.future();
  }

  /**
   * Create/map objects, fields to List<tuple>.
   *
   * @param request
   * @param request
   * @return List<Tuple>
   */
  Future<List<Tuple>> mapTupleUpdate(List<UpdatePolicyNotification> request) {

    Promise<List<Tuple>> promise = Promise.promise();
    List<Tuple> tuples = new ArrayList<>();

    try {

      for (UpdatePolicyNotification each : request) {
        String status = each.getStatus().name();
        UUID requestId = UUID.fromString(each.getRequestId());

        Duration duration = DatatypeFactory.newInstance().newDuration(each.getExpiryDuration());
        JsonObject constraints = each.getConstraints();

        Interval interval =
            Interval.of(
                duration.getYears(),
                duration.getMonths(),
                duration.getDays(),
                duration.getHours(),
                duration.getMinutes(),
                duration.getSeconds());

        tuples.add(Tuple.of(status, interval, constraints, requestId));
      }
    } catch (DatatypeConfigurationException e) {
      LOGGER.error("Fail: {}; {}", INVALID_TUPLE, e.getLocalizedMessage());
      promise.fail(INVALID_TUPLE);
    }

    promise.complete(tuples);
    return promise.future();
  }

  /**
   * Checks the duplicate access requests.
   *
   * @param tuples
   * @return
   */
  public Future<List<Tuple>> checkDuplication(List<Tuple> tuples) {
    Promise<List<Tuple>> promise = Promise.promise();

    List<Tuple> selectTuples = new ArrayList<>();
    for (int i = 0; i < tuples.size(); i++) {
      UUID userId = tuples.get(i).getUUID(0);
      UUID itemId = tuples.get(i).getUUID(1);
      UUID ownerId = tuples.get(i).getUUID(3);
      String status = NotifRequestStatus.PENDING.name();
      selectTuples.add(Tuple.of(userId, itemId, ownerId, status));
    }
    pool.withConnection(
        conn ->
            conn.preparedQuery(SELECT_NOTIF_POLICY_REQUEST)
                .executeBatch(selectTuples)
                .onComplete(
                    dbHandler -> {
                      if (dbHandler.failed()) {
                        LOGGER.error(ERR_DUP_NOTIF_REQ + dbHandler.cause().getLocalizedMessage());
                        promise.fail(dbHandler.cause().getLocalizedMessage());

                      } else if (dbHandler.succeeded()) {
                        RowSet<Row> rows = dbHandler.result();
                        List<UUID> ids = new ArrayList<>();
                        while (rows != null) {
                          rows.iterator()
                              .forEachRemaining(
                                  row -> {
                                    ids.add(row.getUUID(ID));
                                  });
                          rows = rows.next();
                        }

                        if (ids.size() > 0) {
                          LOGGER.error("Fail: {}; Id: {}", DUP_NOTIF_REQ, ids);
                          promise.fail(DUP_NOTIF_REQ);
                        } else {
                          promise.complete(tuples);
                        }
                      }
                    }));

    return promise.future();
  }

  @Override
  public PolicyService createDelegation(
      List<CreateDelegationRequest> request,
      User user,
      JsonObject authDelegateDetails,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    boolean isAuthDelegate = !authDelegateDetails.isEmpty();
    String userId = user.getUserId();
    // check if resources and userIds in request exist in db and have roles as delegate

    if (!(isAuthDelegate || user.getRoles().contains(Roles.PROVIDER))) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_INVALID_ROLES)
              .detail(ERR_DETAIL_LIST_DELEGATE_ROLES)
              .status(401)
              .build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<UUID> users =
        request.stream().map(obj -> UUID.fromString(obj.getUserId())).collect(Collectors.toList());

    Future<Void> checkUserRole = createDelegate.checkUserRoles(users);

    List<String> resServers =
        request.stream().map(CreateDelegationRequest::getResSerId).collect(Collectors.toList());

    Future<Map<String, UUID>> resSerDetail = createDelegate.getResourceServerDetails(resServers);

    // check if user has policy by auth admin
    String finalUserId = userId;
    Future<Boolean> checkAuthPolicy =
        CompositeFuture.all(checkUserRole, resSerDetail)
            .compose(obj -> createDelegate.checkAuthPolicy(finalUserId));

    if (isAuthDelegate) {
      // auth delegate cannot create other auth delegates
      if (resServers.contains(authOptions.getString("authServerUrl"))) {
        Response r =
            new ResponseBuilder()
                .type(URN_INVALID_INPUT)
                .title(ERR_TITLE_AUTH_DELE_CREATE)
                .detail(ERR_TITLE_AUTH_DELE_CREATE)
                .status(403)
                .build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      }
      // if delegate then the delegation should be created by using the providers userId
      userId = authDelegateDetails.getString("providerId");
    }

    String OwnerId = userId;
    Future<List<Tuple>> item =
        checkAuthPolicy.compose(
            ar -> {
              List<Tuple> tuples = new ArrayList<>();
              for (CreateDelegationRequest createDelegationRequest : request) {
                UUID user_id = UUID.fromString(createDelegationRequest.getUserId());
                UUID resource_server_id =
                    resSerDetail.result().get(createDelegationRequest.getResSerId());
                String status = "ACTIVE";
                tuples.add(Tuple.of(OwnerId, user_id, resource_server_id, status));
              }

              return Future.succeededFuture(tuples);
            });

    Future<Boolean> insertDelegations = item.compose(createDelegate::insertItems);

    insertDelegations
        .onSuccess(
            succ -> {
              Response r =
                  new Response.ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title("added delegations")
                      .status(200)
                      .build();
              handler.handle(Future.succeededFuture(r.toJson()));
            })
        .onFailure(
            obj -> {
              if (obj instanceof ComposeException) {
                ComposeException e = (ComposeException) obj;
                handler.handle(Future.succeededFuture(e.getResponse().toJson()));
              } else handler.handle(Future.failedFuture(INTERNALERROR));
            });
    return this;
  }
  private Future<Map<UUID, List<UUID>>> providerToAuthDelegate(Map<String, ResourceObj> resourceObjMap) {
    Promise<Map<UUID, List<UUID>>> promise = Promise.promise();

    Set<UUID> ownerIds = resourceObjMap.values().stream().map(ResourceObj::getOwnerId).collect(Collectors.toSet());
    String url = authOptions.getString("authServerUrl");

    Map<UUID,List<UUID>> providerToAuthDelegate = new HashMap<>();
    for(UUID uuid : ownerIds){
      providerToAuthDelegate.put(uuid,new ArrayList<>());
    }

    pool.withConnection(
        conn ->
            conn.preparedQuery(GET_PROVIDER_AUTH_DELEGATE)
                .execute(
                    Tuple.of(url,
                        (Object) ownerIds.toArray(UUID[]::new))).onSuccess(successHandler->{
                  for(Row row: successHandler){
                    providerToAuthDelegate.put(row.getUUID("provider_id"),
                        List.of(row.getArrayOfUUIDs("user_id_array")));
                  }
                  promise.complete(providerToAuthDelegate);
                }).onFailure(failureHandler->{
                  promise.fail(failureHandler.getLocalizedMessage());
                  LOGGER.debug("Fail to get providerToAuthDelegateIds : "+failureHandler.getLocalizedMessage());
                }));

  return promise.future();
  }
}
