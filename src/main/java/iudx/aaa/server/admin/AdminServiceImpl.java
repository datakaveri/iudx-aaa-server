package iudx.aaa.server.admin;

import static iudx.aaa.server.admin.Constants.ERR_DETAIL_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NOT_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_PROV_REG_ID;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NOT_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.admin.Constants.RESP_STATUS;
import static iudx.aaa.server.admin.Constants.SQL_ADD_NEW_RES_SERVER_ROLE_FOR_ANY_CONSUMER;
import static iudx.aaa.server.admin.Constants.SQL_CREATE_RS_IF_NOT_EXIST;
import static iudx.aaa.server.admin.Constants.SQL_GET_PENDING_PROVIDERS_BY_ID_AND_RS;
import static iudx.aaa.server.admin.Constants.SQL_GET_PROVIDERS_FOR_RS_BY_STATUS;
import static iudx.aaa.server.admin.Constants.SQL_UPDATE_ROLE_STATUS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_CREATED_RS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROVIDER_REGS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROV_STATUS_UPDATE;
import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;

import com.google.common.net.InternetDomainName;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreateRsRequest;
import iudx.aaa.server.apiserver.ProviderUpdateRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.registration.KcAdmin;
import iudx.aaa.server.registration.RegistrationService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Admin Service Implementation.
 *
 * <h1>Admin Service Implementation</h1>
 *
 * <p>The Admin Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.admin.AdminService}.
 */
public class AdminServiceImpl implements AdminService {

  private static final Logger LOGGER = LogManager.getLogger(AdminServiceImpl.class);
  private PgPool pool;
  private KcAdmin kc;
  private RegistrationService registrationService;

  /**
   * Constructor to instantiate {@link AdminServiceImpl}.
   *
   * @param pool instance of {@link PgPool}
   * @param kc instance of {@link KcAdmin}
   * @param registrationService instance of {@link RegistrationService}
   */
  public AdminServiceImpl(PgPool pool, KcAdmin kc, RegistrationService registrationService) {
    this.pool = pool;
    this.kc = kc;
    this.registrationService = registrationService;
  }

  @Override
  public Future<JsonObject> getProviderRegistrations(RoleStatus filter, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!user.getRoles().contains(Roles.ADMIN)) {
      Response r =
          new ResponseBuilder()
              .status(401)
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_NOT_ADMIN)
              .detail(ERR_DETAIL_NOT_ADMIN)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    List<String> resServersAdmin = user.getResServersForRole(Roles.ADMIN);

    Collector<Row, ?, List<JsonObject>> jsonCollector =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> providerRegInfo =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(SQL_GET_PROVIDERS_FOR_RS_BY_STATUS)
                        .collecting(jsonCollector)
                        .execute(
                            Tuple.of(filter.name())
                                .addArrayOfString(resServersAdmin.toArray(String[]::new)))
                        .map(x -> x.value()))
            .compose(
                res -> {
                  if (res.size() > 0) {
                    return Future.succeededFuture(res);
                  }

                  /*
                   * Using ComposeException here to end the compose chain early in case there are no
                   * entries. Not a standard use of ComposeException, but it works out.
                   */
                  Response r =
                      new ResponseBuilder()
                          .status(200)
                          .type(URN_SUCCESS)
                          .title(SUCC_TITLE_PROVIDER_REGS)
                          .arrayResults(new JsonArray())
                          .build();
                  return Future.failedFuture(new ComposeException(r));
                });

    Future<Map<String, JsonObject>> nameDetails =
        providerRegInfo.compose(
            data -> {
              List<String> userIds =
                  data.stream()
                      .map(i -> i.getString("userId"))
                      .distinct()
                      .collect(Collectors.toList());

              return kc.getDetails(userIds);
            });

    nameDetails
        .onSuccess(
            nameDet -> {
              JsonArray resp = new JsonArray();
              providerRegInfo
                  .result()
                  .forEach(
                      regInfo -> {
                        JsonObject obj = regInfo.copy();

                        obj.mergeIn(nameDet.get(regInfo.getString("userId")));
                        resp.add(obj);
                      });

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_PROVIDER_REGS)
                      .status(200)
                      .arrayResults(resp)
                      .build();
              promiseHandler.complete(r.toJson());
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
  public Future<JsonObject> updateProviderRegistrationStatus(
      List<ProviderUpdateRequest> request, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());

    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!user.getRoles().contains(Roles.ADMIN)) {
      Response r =
          new ResponseBuilder()
              .status(401)
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_NOT_ADMIN)
              .detail(ERR_DETAIL_NOT_ADMIN)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    /*
     * Check for duplicate ids - same ID but different status - across objects. OpenAPI
     * can't catch this. Set.add(x) returns false if x already exists in set.
     */

    Set<UUID> ids = new HashSet<UUID>();
    List<UUID> requestedProviderRegIds =
        request.stream().map(r -> UUID.fromString(r.getId())).collect(Collectors.toList());
    List<UUID> duplicates =
        requestedProviderRegIds.stream()
            .filter(id -> ids.add(id) == false)
            .collect(Collectors.toList());

    if (!duplicates.isEmpty()) {
      String firstOffendingId = duplicates.get(0).toString();
      Response resp =
          new ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(ERR_TITLE_DUPLICATE_REQ)
              .detail(firstOffendingId)
              .status(400)
              .build();
      promiseHandler.complete(resp.toJson());
      return promiseHandler.future();
    }

    List<String> resServersAdmin = user.getResServersForRole(Roles.ADMIN);

    Collector<Row, ?, Map<UUID, JsonObject>> collect =
        Collectors.toMap(row -> row.getUUID("id"), row -> row.toJson());

    Tuple tup =
        Tuple.of(ids.toArray(UUID[]::new)).addArrayOfString(resServersAdmin.toArray(String[]::new));

    Future<Map<UUID, JsonObject>> pendingProvDetails =
        pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_GET_PENDING_PROVIDERS_BY_ID_AND_RS)
                    .collecting(collect)
                    .execute(tup)
                    .map(row -> row.value()));

    Future<List<String>> checkProvRegIds =
        pendingProvDetails.compose(
            res -> {
              if (res.size() != ids.size()) {
                UUID firstMissing =
                    ids.stream().filter(id -> !res.containsKey(id)).findFirst().get();

                return Future.failedFuture(
                    new ComposeException(
                        400,
                        URN_INVALID_INPUT,
                        ERR_TITLE_INVALID_PROV_REG_ID,
                        firstMissing.toString()));
              }

              List<String> providerUserIds =
                  res.entrySet().stream()
                      .map(i -> i.getValue().getString("userId"))
                      .collect(Collectors.toList());

              return Future.succeededFuture(providerUserIds);
            });

    List<Tuple> tuple =
        request.stream()
            .map(obj -> Tuple.of(obj.getStatus().name(), UUID.fromString(obj.getId())))
            .collect(Collectors.toList());

    Future<Map<String, JsonObject>> updateStatusAndGetUserDetails =
        checkProvRegIds.compose(
            providerUserIds ->
                pool.withTransaction(
                    conn ->
                        conn.preparedQuery(SQL_UPDATE_ROLE_STATUS)
                            .executeBatch(tuple)
                            .compose(res -> kc.getDetails(providerUserIds))));

    updateStatusAndGetUserDetails
        .onSuccess(
            details -> {
              JsonArray resp = new JsonArray();
              Map<UUID, JsonObject> providerInfo = pendingProvDetails.result();

              request.forEach(
                  obj -> {
                    JsonObject j = providerInfo.get(UUID.fromString(obj.getId()));
                    j.mergeIn(details.get(j.getString("userId")));
                    j.put(RESP_STATUS, obj.getStatus().toString().toLowerCase());
                    resp.add(j);

                    LOGGER.info(
                        "Changed status of role ID {} for provider {} to {}",
                        obj.getId(),
                        j.getString("userId"),
                        obj.getStatus());
                  });

              Response r =
                  new ResponseBuilder()
                      .status(200)
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_PROV_STATUS_UPDATE)
                      .arrayResults(resp)
                      .build();
              promiseHandler.complete(r.toJson());
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
  public Future<JsonObject> createResourceServer(CreateRsRequest request, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());

    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!user.getRoles().contains(Roles.COS_ADMIN)) {
      Response r =
          new ResponseBuilder()
              .status(401)
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_NO_COS_ADMIN_ROLE)
              .detail(ERR_DETAIL_NO_COS_ADMIN_ROLE)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    String name = request.getName();
    String url = request.getUrl();
    String ownerEmail = request.getOwner();
    String domain;

    try {
      /* Validate and get proper domain */
      InternetDomainName parsedDomain = InternetDomainName.from(url);
      domain = parsedDomain.toString();
    } catch (IllegalArgumentException | IllegalStateException e) {
      Response r =
          new ResponseBuilder()
              .status(400)
              .type(URN_INVALID_INPUT)
              .title(ERR_TITLE_INVALID_DOMAIN)
              .detail(ERR_DETAIL_INVALID_DOMAIN)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    Future<JsonObject> adminInfo = registrationService.findUserByEmail(Set.of(ownerEmail));

    Future<UUID> fut =
        adminInfo.compose(
            adminDetails -> {
              UUID ownerId =
                  UUID.fromString(adminDetails.getJsonObject(ownerEmail).getString("keycloakId"));

              return pool.withTransaction(
                  conn ->
                      conn.preparedQuery(SQL_CREATE_RS_IF_NOT_EXIST)
                          .execute(Tuple.of(name, domain, ownerId))
                          .compose(
                              rows -> {
                                if (rows.rowCount() == 0) {
                                  return Future.failedFuture(
                                      new ComposeException(
                                          409,
                                          URN_ALREADY_EXISTS,
                                          ERR_TITLE_DOMAIN_EXISTS,
                                          ERR_DETAIL_DOMAIN_EXISTS));
                                }
                                UUID id = rows.iterator().next().getUUID("id");
                                return Future.succeededFuture(id);
                              })
                          .compose(
                              id ->
                                  conn.preparedQuery(SQL_ADD_NEW_RES_SERVER_ROLE_FOR_ANY_CONSUMER)
                                      .execute(Tuple.of(id))
                                      .map(res -> id)));
            });

    fut.onSuccess(
            id -> {
              JsonObject resp = new JsonObject();
              resp.put("id", id.toString()).put("name", name).put("url", domain);

              JsonObject ownerBlock = adminInfo.result().getJsonObject(ownerEmail);
              ownerBlock.put("id", ownerBlock.remove("keycloakId"));
              resp.put("owner", ownerBlock);

              Response r =
                  new ResponseBuilder()
                      .status(201)
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_CREATED_RS)
                      .objectResults(resp)
                      .build();

              LOGGER.info(
                  "Admin added new resource server {}. All existing users with a consumer"
                      + " role have been given a consumer role for this server"
                      + " (resource server ID {})",
                  url,
                  id);
              promiseHandler.complete(r.toJson());
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
