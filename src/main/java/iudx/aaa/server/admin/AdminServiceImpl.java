package iudx.aaa.server.admin;

import static iudx.aaa.server.admin.Constants.COMPOSE_FAILURE;
import static iudx.aaa.server.admin.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_USER;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.NIL_UUID;
import static iudx.aaa.server.admin.Constants.RESP_ORG;
import static iudx.aaa.server.admin.Constants.RESP_STATUS;
import static iudx.aaa.server.admin.Constants.RESP_USERID;
import static iudx.aaa.server.admin.Constants.SQL_CHECK_ADMIN_OF_SERVER;
import static iudx.aaa.server.admin.Constants.SQL_CREATE_ORG_IF_NOT_EXIST;
import static iudx.aaa.server.admin.Constants.SQL_GET_ORG_DETAILS;
import static iudx.aaa.server.admin.Constants.SQL_GET_PROVIDERS_BY_STATUS;
import static iudx.aaa.server.admin.Constants.SQL_GET_PENDING_PROVIDERS;
import static iudx.aaa.server.admin.Constants.SQL_UPDATE_ROLE_STATUS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_CREATED_ORG;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROVIDER_REGS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROV_STATUS_UPDATE;
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
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreateOrgRequest;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ProviderUpdateRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.KcAdmin;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Admin Service Implementation.
 * <h1>Admin Service Implementation</h1>
 * <p>
 * The Admin Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.admin.AdminService}.
 * </p>
 * 
 */
public class AdminServiceImpl implements AdminService {

  private static final Logger LOGGER = LogManager.getLogger(AdminServiceImpl.class);
  private static String AUTH_SERVER_URL;
  private PgPool pool;
  private KcAdmin kc;
  private PolicyService policyService;

  public AdminServiceImpl(PgPool pool, KcAdmin kc, PolicyService policyService,
      JsonObject options) {
    this.pool = pool;
    this.kc = kc;
    this.policyService = policyService;
    AUTH_SERVER_URL = options.getString(CONFIG_AUTH_URL);
  }

  @Override
  public AdminService getProviderRegistrations(RoleStatus filter, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Future<Boolean> checkAdmin = checkAdminServer(user, AUTH_SERVER_URL).compose(res -> {
      if (res) {
        return Future.succeededFuture(true);
      }
      Response r = new ResponseBuilder().status(401).type(URN_INVALID_ROLE)
          .title(ERR_TITLE_NOT_AUTH_ADMIN).detail(ERR_DETAIL_NOT_AUTH_ADMIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return Future.failedFuture(COMPOSE_FAILURE);
    });

    /*
     * Arrange data into map, key as userId, value as list with 2 UUIDs: index 0 as keycloakId, 1 as
     * organizationId
     */
    final int KcIndex = 0;
    final int OrgIndex = 1;
    Collector<Row, ?, Map<UUID, List<UUID>>> getData = Collectors.toMap(row -> row.getUUID("id"),
        row -> List.of(row.getUUID("keycloak_id"), row.getUUID("organization_id")));

    Future<Map<UUID, List<UUID>>> data = checkAdmin.compose(b -> pool
        .withConnection(conn -> conn.preparedQuery(SQL_GET_PROVIDERS_BY_STATUS).collecting(getData)
            .execute(Tuple.of(filter.name())).map(x -> x.value()).compose(res -> {
              if (res.size() > 0) {
                return Future.succeededFuture(res);
              }

              Response r = new ResponseBuilder().status(200).type(URN_SUCCESS)
                  .title(SUCC_TITLE_PROVIDER_REGS).arrayResults(new JsonArray()).build();
              handler.handle(Future.succeededFuture(r.toJson()));
              return Future.failedFuture(COMPOSE_FAILURE);
            })));

    /* Get array of orgId UUIDs from data map to get org details */
    Function<Map<UUID, List<UUID>>, UUID[]> getOrgArray = (map) -> {
      return map.entrySet().stream().map(val -> val.getValue().get(OrgIndex))
          .collect(Collectors.toList()).toArray(UUID[]::new);
    };

    Collector<Row, ?, Map<UUID, JsonObject>> orgCollect =
        Collectors.toMap(row -> row.getUUID("id"), row -> new JsonObject()
            .put("name", row.getString("name")).put("url", row.getString("url")));

    Future<Map<UUID, JsonObject>> orgDetails = data.compose(dMap -> pool
        .withConnection(conn -> conn.preparedQuery(SQL_GET_ORG_DETAILS).collecting(orgCollect)
            .execute(Tuple.of(getOrgArray.apply(dMap))).map(row -> row.value())));

    /* Get list of String keycloakIds from data map to get user details from keycloak */
    Function<Map<UUID, List<UUID>>, List<String>> getKcList = (map) -> {
      return map.entrySet().stream().map(val -> val.getValue().get(KcIndex).toString())
          .collect(Collectors.toList());
    };

    Future<Map<String, JsonObject>> nameDetails =
        data.compose(dMap -> kc.getDetails(getKcList.apply(dMap)));

    CompositeFuture.all(orgDetails, nameDetails).onSuccess(a -> {
      @SuppressWarnings("unchecked")
      Map<UUID, JsonObject> orgDet = (Map<UUID, JsonObject>) a.list().get(0);
      @SuppressWarnings("unchecked")
      Map<String, JsonObject> nameDet = (Map<String, JsonObject>) a.list().get(1);

      JsonArray resp = new JsonArray();
      data.result().forEach((userId, list) -> {
        JsonObject obj = new JsonObject();
        String keycloakId = list.get(KcIndex).toString();
        UUID orgId = list.get(OrgIndex);

        obj.put(RESP_USERID, userId.toString());
        obj.put(RESP_STATUS, filter.name().toLowerCase());
        obj.mergeIn(nameDet.get(keycloakId));
        obj.put(RESP_ORG, orgDet.get(orgId));
        resp.add(obj);
      });

      Response r = new ResponseBuilder().type(URN_SUCCESS).title(SUCC_TITLE_PROVIDER_REGS)
          .status(200).arrayResults(resp).build();
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

  @Override
  public AdminService updateProviderRegistrationStatus(List<ProviderUpdateRequest> request,
      User user, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Future<Boolean> checkAdmin = checkAdminServer(user, AUTH_SERVER_URL).compose(res -> {
      if (res) {
        return Future.succeededFuture(true);
      }
      Response r = new ResponseBuilder().status(401).type(URN_INVALID_ROLE)
          .title(ERR_TITLE_NOT_AUTH_ADMIN).detail(ERR_DETAIL_NOT_AUTH_ADMIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return Future.failedFuture(COMPOSE_FAILURE);
    });

    Set<UUID> ids =
        request.stream().map(obj -> UUID.fromString(obj.getUserId())).collect(Collectors.toSet());

    Collector<Row, ?, Map<UUID, UUID>> collect =
        Collectors.toMap(row -> row.getUUID("id"), row -> row.getUUID("keycloak_id"));

    Future<Map<UUID, UUID>> pendingKcIds = checkAdmin.compose(success -> pool
        .withConnection(conn -> conn.preparedQuery(SQL_GET_PENDING_PROVIDERS).collecting(collect)
            .execute(Tuple.of(ids.toArray(UUID[]::new))).map(row -> row.value())));

    Future<Map<UUID, UUID>> checkRes = pendingKcIds.compose(res -> {
      if (res.size() != ids.size()) {
        UUID firstMissing = ids.stream().filter(id -> !res.containsKey(id)).findFirst().get();

        Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(ERR_TITLE_INVALID_USER).detail(firstMissing.toString()).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }
      return Future.succeededFuture(res);
    });

    List<UUID> toBeApproved =
        request.stream().filter(obj -> obj.getStatus().equals(RoleStatus.APPROVED))
            .map(obj -> UUID.fromString(obj.getUserId())).collect(Collectors.toList());

    /* Function to get Keycloak IDs of approved providers */
    Function<Map<UUID, UUID>, List<String>> getKcIdToApprove = (map) -> {
      return toBeApproved.stream().map(uid -> map.get(uid).toString()).collect(Collectors.toList());
    };

    /* Function to get Keycloak IDs of all providers in request */
    Function<Map<UUID, UUID>, List<String>> getKcIds = (map) -> {
      return ids.stream().map(uid -> map.get(uid).toString()).collect(Collectors.toList());
    };

    List<Tuple> tuple = request.stream()
        .map(obj -> Tuple.of(obj.getStatus().name(), UUID.fromString(obj.getUserId())))
        .collect(Collectors.toList());

    Future<Map<String, JsonObject>> result = checkRes
        .compose(uidToKc -> pool.withTransaction(conn -> conn.preparedQuery(SQL_UPDATE_ROLE_STATUS)
            .executeBatch(tuple).compose(cc -> kc.approveProvider(getKcIdToApprove.apply(uidToKc)))
            .compose(success -> setAuthAdminPolicy(user, toBeApproved))
            .compose(r -> kc.getDetails(getKcIds.apply(uidToKc)))));

    result.onSuccess(details -> {
      JsonArray resp = new JsonArray();
      Map<UUID, UUID> uidKc = checkRes.result();

      request.forEach(obj -> {
        JsonObject j = obj.toJson();
        UUID kc = uidKc.get(UUID.fromString(obj.getUserId()));
        j.mergeIn(details.get(kc.toString()));
        j.put(RESP_STATUS, j.remove(RESP_STATUS).toString().toLowerCase());
        resp.add(j);
        LOGGER.info("Changed status of " + obj.getUserId() + " to " + obj.getStatus());
      });

      Response r = new ResponseBuilder().status(200).type(URN_SUCCESS)
          .title(SUCC_TITLE_PROV_STATUS_UPDATE).arrayResults(resp).build();
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
   * Calls the createPolicy method in PolicyService to create auth admin policies for the approved
   * providers.
   * 
   * @param user The User object of the Auth Admin.
   * @param approvedProviders List of user IDs of approved providers
   * @return void future
   */
  private Future<Void> setAuthAdminPolicy(User user, List<UUID> approvedProviders) {

    Promise<Void> response = Promise.promise();
    /* Exit early no providers to be approved */
    if (approvedProviders.size() == 0) {
      response.complete();
      return response.future();
    }

    List<CreatePolicyRequest> request = approvedProviders.stream().map(id -> {
      JsonObject obj = new JsonObject();
      obj.put("userId", id.toString());
      obj.put("itemId", AUTH_SERVER_URL);
      obj.put("constraints", new JsonObject());
      obj.put("itemType", "resource_server");
      CreatePolicyRequest req = new CreatePolicyRequest(obj);
      return req;
    }).collect(Collectors.toList());

    Promise<JsonObject> promise = Promise.promise();
    policyService.createPolicy(request, user,new JsonObject(), promise);
    promise.future().onSuccess(res -> {
      if (res.getString("type").equals(URN_SUCCESS.toString())) {
        response.complete();
      } else {
        response.fail("Failed to set admin policy");
      }
    }).onFailure(res -> {
      response.fail("Failed to set admin policy");
    });
    return response.future();
  }

  @Override
  public AdminService createOrganization(CreateOrgRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String name = request.getName();
    String url = request.getUrl().toLowerCase();
    String domain;

    Future<Boolean> checkAdmin = checkAdminServer(user, AUTH_SERVER_URL).compose(res -> {
      if (res) {
        return Future.succeededFuture(true);
      }
      Response r = new ResponseBuilder().status(401).type(URN_INVALID_ROLE)
          .title(ERR_TITLE_NOT_AUTH_ADMIN).detail(ERR_DETAIL_NOT_AUTH_ADMIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return Future.failedFuture(COMPOSE_FAILURE);
    });

    try {
      /* Validate and get proper domain */
      InternetDomainName parsedDomain = InternetDomainName.from(url).topDomainUnderRegistrySuffix();
      domain = parsedDomain.toString();
    } catch (IllegalArgumentException | IllegalStateException e) {
      Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
          .title(ERR_TITLE_INVALID_DOMAIN).detail(ERR_DETAIL_INVALID_DOMAIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Future<RowSet<Row>> fut = checkAdmin.compose(success -> pool.withConnection(
        conn -> conn.preparedQuery(SQL_CREATE_ORG_IF_NOT_EXIST).execute(Tuple.of(name, domain))));

    fut.onSuccess(rows -> {
      if (rows.rowCount() == 0) {
        Response r = new ResponseBuilder().status(409).type(URN_ALREADY_EXISTS)
            .title(ERR_TITLE_DOMAIN_EXISTS).detail(ERR_DETAIL_DOMAIN_EXISTS).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return;
      }

      String id = rows.iterator().next().getUUID("id").toString();
      JsonObject resp = new JsonObject();
      resp.put("id", id).put("name", name).put("url", domain);

      Response r = new ResponseBuilder().status(201).type(URN_SUCCESS).title(SUCC_TITLE_CREATED_ORG)
          .objectResults(resp).build();
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
   * Check if a user is an admin for a particular server.
   * 
   * @param user The user object
   * @param serverUrl The URL/domain of the particuar server
   * @return Future of Boolean type
   */
  private Future<Boolean> checkAdminServer(User user, String serverUrl) {
    Promise<Boolean> p = Promise.promise();

    if (!user.getRoles().contains(Roles.ADMIN)) {
      p.complete(false);
      return p.future();
    }

    pool.withConnection(conn -> conn.preparedQuery(SQL_CHECK_ADMIN_OF_SERVER)
        .execute(Tuple.of(user.getUserId(), serverUrl)).map(row -> row.size()))
        .onSuccess(size -> p.complete(size == 0 ? false : true))
        .onFailure(error -> p.fail(error.getMessage()));

    return p.future();
  }
}
