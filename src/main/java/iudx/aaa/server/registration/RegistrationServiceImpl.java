package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CLIENT_SECRET_BYTES;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_EXISTING_ROLE_FOR_RS;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_NOT_FOUND_EMAILS;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_NOT_FOUND_RS_URLS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_CONSUMER_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_DEFAULT_CLIENT_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_PROVIDER_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_RS_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_DEFAULT_CLIENT_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ROLE_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_RS_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.NIL_PHONE;
import static iudx.aaa.server.registration.Constants.PROVIDER_PENDING_MESG;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_NAME;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SQL_CHECK_CLIENT_ID_EXISTS;
import static iudx.aaa.server.registration.Constants.SQL_CHECK_DEFAULT_CLIENT_EXISTS;
import static iudx.aaa.server.registration.Constants.SQL_CHECK_PENDING_REJECTED_PROVIDER_ROLES;
import static iudx.aaa.server.registration.Constants.SQL_CHECK_USER_HAS_PROV_CONS_ROLE_FOR_RS;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_CLIENT;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_ROLE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_USER_IF_NOT_EXISTS;
import static iudx.aaa.server.registration.Constants.SQL_GET_ALL_RS;
import static iudx.aaa.server.registration.Constants.SQL_GET_CLIENTS_FORMATTED;
import static iudx.aaa.server.registration.Constants.SQL_GET_PHONE;
import static iudx.aaa.server.registration.Constants.SQL_GET_RS_AND_APDS_FOR_REVOKE;
import static iudx.aaa.server.registration.Constants.SQL_GET_RS_IDS_BY_URL;
import static iudx.aaa.server.registration.Constants.SQL_UPDATE_CLIENT_SECRET;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_ADDED_ROLES;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_CREATED_DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_REGEN_CLIENT_SECRET;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_RS_READ;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_FOUND;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_READ;
import static iudx.aaa.server.registration.Constants.UUID_REGEX;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.AddRolesRequest;
import iudx.aaa.server.apiserver.ResetClientSecretRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.token.TokenService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Registration Service Implementation.
 *
 * <h1>Registration Service Implementation</h1>
 *
 * <p>The Registration Service implementation in the IUDX AAA Server implements the definitions of
 * the {@link iudx.aaa.server.registration.RegistrationService}.
 */
public class RegistrationServiceImpl implements RegistrationService {

  private static final Logger LOGGER = LogManager.getLogger(RegistrationServiceImpl.class);

  private PgPool pool;
  private KcAdmin kc;
  private TokenService tokenService;
  private static String COS_URL = "";
  private static List<String> SERVERS_OMITTED_FROM_TOKEN_REVOKE = new ArrayList<String>();

  private SecureRandom randomSource;

  public RegistrationServiceImpl(
      PgPool pool, KcAdmin kc, TokenService tokenService, JsonObject options) {
    this.pool = pool;
    this.kc = kc;
    this.tokenService = tokenService;
    COS_URL = options.getString(CONFIG_COS_URL);
    SERVERS_OMITTED_FROM_TOKEN_REVOKE =
        options.getJsonArray(CONFIG_OMITTED_SERVERS).stream()
            .map(x -> (String) x)
            .collect(Collectors.toList());

    randomSource = new SecureRandom();
  }

  @Override
  public Future<JsonObject> addRoles(AddRolesRequest request, User user) {

    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    List<Roles> requestedRoles = request.getRolesToRegister();
    final String phoneInReq = request.getPhone();
    final JsonObject userInfo = request.getUserInfo();

    List<String> ownedRsForProviderRole = user.getResServersForRole(Roles.PROVIDER);
    List<String> requestedRsForProviderRole = request.getProvider();

    List<String> ownedRsForConsumerRole = user.getResServersForRole(Roles.CONSUMER);
    List<String> requestedRsForConsumerRole = request.getConsumer();

    Set<String> allRequestedRs =
        Stream.concat(requestedRsForConsumerRole.stream(), requestedRsForProviderRole.stream())
            .collect(Collectors.toSet());

    if (requestedRoles.contains(Roles.PROVIDER)) {
      List<String> duplicateProviderRs =
          ownedRsForProviderRole.stream()
              .filter(rs -> requestedRsForProviderRole.contains(rs))
              .collect(Collectors.toList());

      if (!duplicateProviderRs.isEmpty()) {
        Response r =
            new ResponseBuilder()
                .status(409)
                .type(URN_ALREADY_EXISTS)
                .title(ERR_TITLE_ROLE_FOR_RS_EXISTS)
                .detail(ERR_DETAIL_PROVIDER_FOR_RS_EXISTS)
                .errorContext(
                    new JsonObject()
                        .put(ERR_CONTEXT_EXISTING_ROLE_FOR_RS, new JsonArray(duplicateProviderRs)))
                .build();
        promiseHandler.complete(r.toJson());
        return promiseHandler.future();
      }
    }

    if (requestedRoles.contains(Roles.CONSUMER)) {
      List<String> duplicateConsumerRs =
          ownedRsForConsumerRole.stream()
              .filter(rs -> requestedRsForConsumerRole.contains(rs))
              .collect(Collectors.toList());

      if (!duplicateConsumerRs.isEmpty()) {
        Response r =
            new ResponseBuilder()
                .status(409)
                .type(URN_ALREADY_EXISTS)
                .title(ERR_TITLE_ROLE_FOR_RS_EXISTS)
                .detail(ERR_DETAIL_CONSUMER_FOR_RS_EXISTS)
                .errorContext(
                    new JsonObject()
                        .put(ERR_CONTEXT_EXISTING_ROLE_FOR_RS, new JsonArray(duplicateConsumerRs)))
                .build();
        promiseHandler.complete(r.toJson());
        return promiseHandler.future();
      }
    }

    Future<String> email = kc.getEmailId(user.getUserId());
    Collector<Row, ?, Map<String, UUID>> rsCollector =
        Collectors.toMap(row -> row.getString("url"), row -> row.getUUID("id"));

    Future<Map<String, UUID>> getRequestedRs =
        pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_GET_RS_IDS_BY_URL)
                    .collecting(rsCollector)
                    .execute(Tuple.of(allRequestedRs.toArray(String[]::new)))
                    .map(res -> res.value()));

    Future<Void> checkEmailAndResourceServerUrls =
        CompositeFuture.all(email, getRequestedRs)
            .compose(
                arr -> {
                  String emailId = arr.resultAt(0);
                  Map<String, UUID> rsDetails = arr.resultAt(1);

                  if (emailId.length() == 0) {
                    return Future.failedFuture(
                        new ComposeException(
                            400, URN_INVALID_INPUT, ERR_TITLE_USER_NOT_KC, ERR_DETAIL_USER_NOT_KC));
                  }

                  List<String> missingRs =
                      allRequestedRs.stream()
                          .filter(rs -> !rsDetails.containsKey(rs))
                          .collect(Collectors.toList());

                  if (!missingRs.isEmpty()) {
                    Response resp =
                        new ResponseBuilder()
                            .type(Urn.URN_INVALID_INPUT)
                            .status(400)
                            .title(ERR_TITLE_RS_NO_EXIST)
                            .detail(ERR_DETAIL_RS_NO_EXIST)
                            .errorContext(
                                new JsonObject()
                                    .put(ERR_CONTEXT_NOT_FOUND_RS_URLS, new JsonArray(missingRs)))
                            .build();
                    return Future.failedFuture(new ComposeException(resp));
                  }

                  return Future.succeededFuture();
                });

    Future<Void> checkForProviderRejectedPendingRegs =
        checkEmailAndResourceServerUrls.compose(
            roleListTup -> {
              if (!requestedRoles.contains(Roles.PROVIDER)) {
                return Future.succeededFuture(roleListTup);
              }

              Map<UUID, String> requestedRsIdsToUrl =
                  requestedRsForProviderRole.stream()
                      .collect(
                          Collectors.toMap(url -> getRequestedRs.result().get(url), url -> url));

              Collector<Row, ?, Map<String, List<String>>> pendingRejectedUrlsCollector =
                  Collectors.groupingBy(
                      row -> row.getString("status").toLowerCase(),
                      Collectors.mapping(
                          row -> requestedRsIdsToUrl.get(row.getUUID("resource_server_id")),
                          Collectors.toList()));

              UUID[] requestedRsIds = requestedRsIdsToUrl.keySet().toArray(UUID[]::new);

              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(SQL_CHECK_PENDING_REJECTED_PROVIDER_ROLES)
                          .collecting(pendingRejectedUrlsCollector)
                          .execute(Tuple.of(requestedRsIds, user.getUserId()))
                          .map(succ -> succ.value())
                          .compose(
                              map -> {
                                if (map.isEmpty()) {
                                  return Future.succeededFuture();
                                }

                                JsonObject offendingRs = new JsonObject();
                                map.forEach(
                                    (status, urls) -> offendingRs.put(status, new JsonArray(urls)));

                                Response resp =
                                    new ResponseBuilder()
                                        .type(Urn.URN_INVALID_INPUT)
                                        .status(403)
                                        .title(ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS)
                                        .detail(ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS)
                                        .errorContext(offendingRs)
                                        .build();
                                return Future.failedFuture(new ComposeException(resp));
                              }));
            });

    /* get phone number if available, else return empty string */
    Future<JsonObject> phoneDetails =
        checkForProviderRejectedPendingRegs.compose(
            i ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(SQL_GET_PHONE)
                            .execute(Tuple.of(user.getUserId()))
                            .map(
                                rows ->
                                    rows.iterator().hasNext()
                                        ? rows.iterator().next().toJson()
                                        : new JsonObject().put(RESP_PHONE, ""))));

    Collector<Row, ?, List<JsonObject>> clientCollector =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    /* get clients if available */
    Future<List<JsonObject>> clientDetails =
        checkForProviderRejectedPendingRegs.compose(
            i ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(SQL_GET_CLIENTS_FORMATTED)
                            .collecting(clientCollector)
                            .execute(Tuple.of(user.getUserId()))
                            .map(res -> res.value())));

    Future<List<Tuple>> createRoleTuple =
        CompositeFuture.all(phoneDetails, clientDetails)
            .compose(
                res -> {
                  List<Tuple> roleTupList = new ArrayList<Tuple>();
                  Map<String, UUID> rsDetails = getRequestedRs.result();

                  List<Tuple> consumerTup =
                      requestedRsForConsumerRole.stream()
                          .map(
                              url ->
                                  Tuple.of(
                                      user.getUserId(),
                                      Roles.CONSUMER,
                                      rsDetails.get(url),
                                      RoleStatus.APPROVED))
                          .collect(Collectors.toList());
                  roleTupList.addAll(consumerTup);

                  List<Tuple> providerTup =
                      requestedRsForProviderRole.stream()
                          .map(
                              url ->
                                  Tuple.of(
                                      user.getUserId(),
                                      Roles.PROVIDER,
                                      rsDetails.get(url),
                                      RoleStatus.PENDING))
                          .collect(Collectors.toList());
                  roleTupList.addAll(providerTup);

                  return Future.succeededFuture(roleTupList);
                });

    /* Insertion into users, roles tables */
    Future<Void> insertUserAndRoles =
        createRoleTuple.compose(
            rolesListTuple ->
                pool.withTransaction(
                    conn ->
                        conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                            .execute(Tuple.of(user.getUserId(), phoneInReq, userInfo))
                            .compose(
                                userCreated ->
                                    conn.preparedQuery(SQL_CREATE_ROLE)
                                        .executeBatch(rolesListTuple)
                                        .mapEmpty())));

    insertUserAndRoles
        .onSuccess(
            inserted -> {
              List<Roles> existingRoles = user.getRoles();
              Map<String, JsonArray> existingRolesToRsMap = user.getRolesToRsMapping();

              if (requestedRoles.contains(Roles.CONSUMER)) {
                if (existingRoles.contains(Roles.CONSUMER)) {
                  List<String> oldAndNewRsForConsumer = new ArrayList<String>();

                  oldAndNewRsForConsumer.addAll(ownedRsForConsumerRole);
                  oldAndNewRsForConsumer.addAll(requestedRsForConsumerRole);

                  // the role name NEEDS to be in lower case, since that's how
                  // it's in the map. If kept as uppercase, the key `consumer`
                  // will not get reset and a duplicate key error WILL occur.
                  existingRolesToRsMap.put(
                      Roles.CONSUMER.toString().toLowerCase(),
                      new JsonArray(oldAndNewRsForConsumer));
                } else {
                  existingRoles.add(Roles.CONSUMER);
                  existingRolesToRsMap.put(
                      Roles.CONSUMER.toString().toLowerCase(),
                      new JsonArray(requestedRsForConsumerRole));
                }
              }

              User u =
                  new UserBuilder()
                      .name(user.getName().get("firstName"), user.getName().get("lastName"))
                      .roles(existingRoles)
                      .rolesToRsMapping(existingRolesToRsMap)
                      .userId(user.getUserId())
                      .build();

              JsonObject payload = u.toJsonResponse().put(RESP_EMAIL, email.result());

              String phoneInDb = phoneDetails.result().getString(RESP_PHONE);

              // if phoneInDb is NIL_PHONE - user did not enter number first time and cannot update
              // it
              // if phoneInDb is blank - user is coming first time, so use phoneInReq if it's not
              // NIL_PHONE
              if (!phoneInDb.equals(NIL_PHONE) && !phoneInDb.isBlank()) {
                payload.put(RESP_PHONE, phoneInDb);
              } else if (phoneInDb.isBlank() && !phoneInReq.equals(NIL_PHONE)) {
                payload.put(RESP_PHONE, phoneInReq);
              }

              if (!clientDetails.result().isEmpty()) {
                payload.put(RESP_CLIENT_ARR, new JsonArray(clientDetails.result()));
              }

              String title = SUCC_TITLE_ADDED_ROLES;
              if (requestedRoles.contains(Roles.PROVIDER)) {
                title = title + PROVIDER_PENDING_MESG;
              }

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(title)
                      .status(200)
                      .objectResults(payload)
                      .build();
              promiseHandler.complete(r.toJson());

              LOGGER.info("Added roles {} for {}", requestedRoles, user.getUserId());
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
  public Future<JsonObject> listUser(User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (user.getRoles().isEmpty()) {
      Response r =
          new ResponseBuilder()
              .status(404)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_NO_APPROVED_ROLES)
              .detail(ERR_DETAIL_NO_APPROVED_ROLES)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    // cos admin may not have entry in DB, so if row count = 0, return phone w/ NIL_PHONE number
    Future<JsonObject> phoneDetails =
        pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_GET_PHONE)
                    .execute(Tuple.of(user.getUserId()))
                    .map(
                        rows ->
                            rows.iterator().hasNext()
                                ? rows.iterator().next().toJson()
                                : new JsonObject().put(RESP_PHONE, NIL_PHONE)));

    Future<String> email = kc.getEmailId(user.getUserId());

    Collector<Row, ?, List<JsonObject>> clientDetails =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> clientQuery =
        pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_GET_CLIENTS_FORMATTED)
                    .collecting(clientDetails)
                    .execute(Tuple.of(user.getUserId()))
                    .map(res -> res.value()));

    CompositeFuture.all(phoneDetails, clientQuery, email)
        .onSuccess(
            obj -> {
              JsonObject details = (JsonObject) obj.list().get(0);
              @SuppressWarnings("unchecked")
              List<JsonObject> clients = (List<JsonObject>) obj.list().get(1);
              String emailId = (String) obj.list().get(2);

              if (emailId.length() == 0) {
                Response r =
                    new ResponseBuilder()
                        .status(400)
                        .type(URN_INVALID_INPUT)
                        .title(ERR_TITLE_USER_NOT_KC)
                        .detail(ERR_DETAIL_USER_NOT_KC)
                        .build();
                promiseHandler.complete(r.toJson());
              }

              JsonObject response = user.toJsonResponse();
              response.put(RESP_EMAIL, emailId);

              String phone = (String) details.remove("phone");
              if (!phone.equals(NIL_PHONE)) {
                response.put(RESP_PHONE, phone);
              }

              if (!clients.isEmpty()) {
                response.put(RESP_CLIENT_ARR, new JsonArray(clients));
              }

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_USER_READ)
                      .status(200)
                      .objectResults(response)
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
  public Future<JsonObject> resetClientSecret(ResetClientSecretRequest request, User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (user.getRoles().isEmpty()) {
      Response r =
          new ResponseBuilder()
              .status(404)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_NO_APPROVED_ROLES)
              .detail(ERR_DETAIL_NO_APPROVED_ROLES)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    Promise<JsonObject> modification = Promise.promise();

    resetClientSecret(user, request, modification);

    /* After successful modification, get user details for response */
    Future<JsonObject> modified = modification.future();

    Future<JsonObject> phoneDetails =
        modified.compose(
            x ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(SQL_GET_PHONE)
                            .execute(Tuple.of(user.getUserId()))
                            .map(rows -> rows.iterator().next().toJson())));

    Collector<Row, ?, List<JsonObject>> clientDetails =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> clientQuery =
        modified.compose(
            x ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(SQL_GET_CLIENTS_FORMATTED)
                            .collecting(clientDetails)
                            .execute(Tuple.of(user.getUserId()))
                            .map(res -> res.value())));

    /* TODO: kc.getEmailId is slow, already being performed at addRole. Consider using once only */
    Future<String> getEmail = modified.compose(x -> kc.getEmailId(user.getUserId()));

    CompositeFuture.all(phoneDetails, clientQuery, getEmail)
        .onSuccess(
            obj -> {
              JsonObject details = (JsonObject) obj.list().get(0);
              @SuppressWarnings("unchecked")
              List<JsonObject> clients = (List<JsonObject>) obj.list().get(1);
              String email = (String) obj.list().get(2);

              List<Roles> approvedRoles = new ArrayList<Roles>();
              approvedRoles.addAll(user.getRoles());

              JsonObject modifiedInfo = modified.result();

              String updatedClientId = modifiedInfo.getString(RESP_CLIENT_ID);
              String clientSecret = modifiedInfo.getString(RESP_CLIENT_SC);

              for (int i = 0; i < clients.size(); i++) {
                JsonObject cli = clients.get(i);
                if (cli.getString(RESP_CLIENT_ID).equals(updatedClientId)) {
                  clients.set(i, cli.put(RESP_CLIENT_SC, clientSecret));
                }
              }

              User u =
                  new UserBuilder()
                      .name(user.getName().get("firstName"), user.getName().get("lastName"))
                      .roles(approvedRoles)
                      .userId(user.getUserId())
                      .build();

              JsonObject response = u.toJsonResponse();
              response.put(RESP_EMAIL, email);
              response.put(RESP_CLIENT_ARR, new JsonArray(clients));

              String phone = (String) details.remove("phone");
              if (!phone.equals(NIL_PHONE)) {
                response.put(RESP_PHONE, phone);
              }

              LOGGER.info(
                  "Reset client secret for user {} for client ID {}",
                  u.getUserId(),
                  request.getClientId());

              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_REGEN_CLIENT_SECRET)
                      .status(200)
                      .objectResults(response)
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
  public Future<JsonObject> listResourceServer() {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    Collector<Row, ?, List<JsonObject>> orgCollect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> rsFuture =
        pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_GET_ALL_RS)
                    .collecting(orgCollect)
                    .execute()
                    .map(rows -> rows.value()));

    Future<JsonObject> ownerFuture =
        rsFuture.compose(
            res -> {
              List<String> ownerIds =
                  res.stream().map(obj -> obj.getString("owner_id")).collect(Collectors.toList());

              return getUserDetails(ownerIds);
            });

    Future<JsonArray> result =
        ownerFuture.compose(
            ownerDetails -> {
              List<JsonObject> rsDetails = rsFuture.result();
              JsonArray arr = new JsonArray();

              rsDetails.forEach(
                  rs -> {
                    JsonObject ownerBlock = ownerDetails.getJsonObject(rs.getString("owner_id"));
                    ownerBlock.put("id", rs.remove("owner_id"));
                    rs.put("owner", ownerBlock);
                    arr.add(rs);
                  });
              return Future.succeededFuture(arr);
            });

    result
        .onSuccess(
            res -> {
              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_RS_READ)
                      .status(200)
                      .arrayResults(res)
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
  public Future<JsonObject> getUserDetails(List<String> userIds) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (userIds.isEmpty()) {
      promiseHandler.complete(new JsonObject());
      return promiseHandler.future();
    }

    Set<UUID> unique = new HashSet<UUID>();

    for (String id : userIds) {
      if (id == null || !id.matches(UUID_REGEX)) {
        promiseHandler.fail("Invalid UUID");
        return promiseHandler.future();
      }
      unique.add(UUID.fromString(id));
    }

    List<String> ids = unique.stream().map(i -> i.toString()).collect(Collectors.toList());

    Future<Map<String, JsonObject>> details = kc.getDetails(ids);

    details
        .onSuccess(
            idToDetails -> {
              JsonObject userDetails = new JsonObject();

              idToDetails.forEach((uid, jsonDet) -> userDetails.put(uid, jsonDet));
              promiseHandler.complete(userDetails);
            })
        .onFailure(
            e -> {
              if (e instanceof ComposeException) {
                promiseHandler.fail(e.getMessage());
                return;
              }
              LOGGER.error(e.getMessage());
              promiseHandler.fail("Internal error");
            });

    return promiseHandler.future();
  }

  /**
   * Reset client secret of a particular client ID for a user. The promise argument succeeds with a
   * JSON object containing the client ID <i>clientId</i> and the regenerated client secret
   * <i>clientSecret</i>. The promise argument fails with a ComposeException in case of an expected
   * error.
   *
   * @param user The User object for the user who wants to reset client secret
   * @param request The UpdateProfileRequest object containing the client ID
   * @param promise A Promise indicating the success/failure of the operation
   */
  public void resetClientSecret(
      User user, ResetClientSecretRequest request, Promise<JsonObject> promise) {
    UUID userId = UUID.fromString(user.getUserId());
    UUID clientId = UUID.fromString(request.getClientId());

    Tuple tuple = Tuple.of(clientId, userId);
    Future<Void> checkClientId =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(SQL_CHECK_CLIENT_ID_EXISTS)
                        .execute(tuple)
                        .map(row -> row.iterator().next().getBoolean(0)))
            .compose(
                res -> {
                  if (!res) {
                    Response r =
                        new ResponseBuilder()
                            .status(404)
                            .type(URN_INVALID_INPUT)
                            .title(ERR_TITLE_INVALID_CLI_ID)
                            .detail(ERR_DETAIL_INVALID_CLI_ID)
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  }
                  return Future.succeededFuture();
                });

    Future<List<RevokeToken>> tokenRevokeReq =
        checkClientId.compose(
            success -> {
              /* Collector to create list of TokenRevoke requests from list of resource_server urls */
              Collector<Row, ?, List<RevokeToken>> getTokenRevokeReqList =
                  Collectors.mapping(
                      row -> {
                        JsonObject revokeReq = new JsonObject().put("rsUrl", row.getString("url"));
                        return new RevokeToken(revokeReq);
                      },
                      Collectors.toList());

              /*
               * We can choose to omit some servers from the revocation required during client secret regen
               * by adding them to the config. (Currently no server needs to be revoked since we don't
               * bother if a revocation succeeds (HTTP 200) or fails (any other status code, DNS error))
               */
              List<String> omittedServers = SERVERS_OMITTED_FROM_TOKEN_REVOKE;
              omittedServers.add(COS_URL);

              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(SQL_GET_RS_AND_APDS_FOR_REVOKE)
                          .collecting(getTokenRevokeReqList)
                          .execute(Tuple.of(omittedServers.toArray(String[]::new)))
                          .map(res -> res.value()));
            });

    Future<CompositeFuture> tokenRevokeResult =
        tokenRevokeReq.compose(
            revReq -> {
              @SuppressWarnings("rawtypes")
              List<Future> futures =
                  revReq.stream()
                      .map(req -> callTokenRevoke(user, req))
                      .collect(Collectors.toList());
              return CompositeFuture.all(futures);
            });

    tokenRevokeResult
        .compose(
            revokedAll -> {
              /*
               * TODO: callTokenRevoke only fails in case there's an internal error from the tokenRevoke
               * service. It returns all succeeded futures of Boolean type, true if 200 OK, false if not. We
               * currently do not check if the bool is true or false, we only know that the future has
               * succeeded. Later on, we need to act on instances where the bool = false, i.e. the token
               * revoke call to that particular server has failed. retry logic?, store info about revoke and
               * expose an API to servers?
               */
              byte[] randBytes = new byte[CLIENT_SECRET_BYTES];
              randomSource.nextBytes(randBytes);
              String clientSecret = Hex.encodeHexString(randBytes);
              String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);
              Tuple tup = Tuple.of(hashedClientSecret, clientId, userId);

              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(SQL_UPDATE_CLIENT_SECRET).execute(tup).map(clientSecret));
            })
        .onSuccess(
            cliSec -> {
              JsonObject clientDets =
                  new JsonObject()
                      .put(RESP_CLIENT_ID, clientId.toString())
                      .put(RESP_CLIENT_SC, cliSec);
              promise.complete(clientDets);
            })
        .onFailure(
            e -> {
              if (e instanceof ComposeException) {
                promise.fail(e);
                return;
              }

              LOGGER.error(e.getMessage());
              promise.fail("Internal error");
            });
    return;
  }

  /**
   * Calls the revokeToken method in the TokenService. A Boolean future is returned. A succeeded
   * future with <i>true</i> is returned if the revoke is successful.A A succeeded future with
   * <i>false</i> is returned if the revocation fails due to expected errors e.g. server not
   * reachable, responded incorrectly etc. <b>A failed future is returned if the revocation fails
   * unexpected like e.g. due to an internal error.</b>
   *
   * @param user The User object for the user for whom the tokens must be revoked
   * @param request A RevokeToken request object containing the server URL to be revoked
   * @return a Boolean future
   */
  private Future<Boolean> callTokenRevoke(User user, RevokeToken request) {
    Promise<Boolean> response = Promise.promise();

    tokenService
        .revokeToken(request, user)
        .onSuccess(
            resp -> {
              if (resp.getString("type").equals(URN_SUCCESS.toString())) {
                response.complete(true);
              } else {
                response.complete(false);
                LOGGER.error("Failed to revoke tokens on {}", request.getRsUrl());
              }
            })
        .onFailure(
            err -> {
              response.fail("Future failed - Failed to revoke tokens on " + request.getRsUrl());
              LOGGER.error(err.getLocalizedMessage());
            });
    return response.future();
  }

  @Override
  public Future<JsonObject> findUserByEmail(Set<String> emailIds) {

    Promise<JsonObject> promiseHandler = Promise.promise();

    if (emailIds.isEmpty()) {
      promiseHandler.complete(new JsonObject());
      return promiseHandler.future();
    }

    Map<String, Future<JsonObject>> kcInfoMap =
        emailIds.stream().collect(Collectors.toMap(id -> id, id -> kc.findUserByEmail(id)));

    @SuppressWarnings("rawtypes")
    List<Future> kcFutures = new ArrayList<Future>(kcInfoMap.values());

    Future<Void> checkAllEmailsExist =
        CompositeFuture.all(kcFutures)
            .compose(
                res -> {
                  List<String> missingEmails =
                      kcInfoMap.entrySet().stream()
                          .filter(i -> i.getValue().result().isEmpty())
                          .map(i -> i.getKey())
                          .collect(Collectors.toList());

                  if (!missingEmails.isEmpty()) {
                    Response resp =
                        new ResponseBuilder()
                            .type(Urn.URN_INVALID_INPUT)
                            .status(400)
                            .title(ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK)
                            .detail(ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK)
                            .errorContext(
                                new JsonObject()
                                    .put(
                                        ERR_CONTEXT_NOT_FOUND_EMAILS, new JsonArray(missingEmails)))
                            .build();
                    return Future.failedFuture(new ComposeException(resp));
                  }

                  return Future.succeededFuture();
                });

    Future<Void> insertIfNotExists =
        checkAllEmailsExist.compose(
            res -> {
              List<Tuple> tups = new ArrayList<Tuple>();

              kcInfoMap.forEach(
                  (emailId, fut) -> {
                    UUID userId = UUID.fromString(fut.result().getString("keycloakId"));
                    JsonObject emptyUserInfo = new JsonObject();
                    Tuple tup = Tuple.of(userId, NIL_PHONE, emptyUserInfo);
                    tups.add(tup);
                  });

              Future<RowSet<Row>> inserting =
                  pool.withTransaction(
                      conn -> conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS).executeBatch(tups));

              Future<Void> logIfInserted =
                  inserting.compose(
                      batchRows -> {
                        // need to get row result like this when using `executeBatch`
                        RowSet<Row> rows = batchRows;
                        while (rows != null) {
                          rows.iterator()
                              .forEachRemaining(
                                  row -> {
                                    LOGGER.info(
                                        "Added new user to COS with user ID {}", row.getUUID("id"));
                                  });
                          rows = rows.next();
                        }
                        return Future.succeededFuture();
                      });

              return logIfInserted;
            });

    insertIfNotExists
        .onSuccess(
            res -> {
              JsonObject result = new JsonObject();

              kcInfoMap.forEach(
                  (emailId, fut) -> {
                    result.put(emailId, fut.result());
                  });
              promiseHandler.complete(result);
            })
        .onFailure(
            err -> {
              promiseHandler.fail(err);
            });

    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> getDefaultClientCredentials(User user) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (user.getRoles().isEmpty()) {
      Response r =
          new ResponseBuilder()
              .status(404)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_NO_APPROVED_ROLES)
              .detail(ERR_DETAIL_NO_APPROVED_ROLES)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    UUID userId = UUID.fromString(user.getUserId());
    Tuple tuple = Tuple.of(userId);

    Collector<Row, ?, List<UUID>> clientIdCollector =
        Collectors.mapping(row -> row.getUUID("client_id"), Collectors.toList());

    Future<Void> checkDefaultClientId =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(SQL_CHECK_DEFAULT_CLIENT_EXISTS)
                        .collecting(clientIdCollector)
                        .execute(tuple)
                        .map(res -> res.value()))
            .compose(
                cidList -> {
                  if (!cidList.isEmpty()) {
                    Response r =
                        new ResponseBuilder()
                            .status(409)
                            .type(URN_ALREADY_EXISTS)
                            .title(ERR_TITLE_DEFAULT_CLIENT_EXISTS)
                            .detail(ERR_DETAIL_DEFAULT_CLIENT_EXISTS)
                            .errorContext(
                                new JsonObject().put("clientId", cidList.get(0).toString()))
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  }
                  return Future.succeededFuture();
                });
    /*
     * In case a COS admin wants to get client creds, they **may** not have an entry in the `users`
     * table - since COS admins just need to be registered on Keycloak and are identified by their
     * user ID in the config. Inserting into the `user_clients` table will throw an error due to
     * foreign key constrains.
     *
     * Hence to avoid this edge case we try to insert the COS admin as a user into the table.
     */
    Future<Void> cosAdminEdgeCase =
        checkDefaultClientId.compose(
            res -> {
              List<Roles> roles = user.getRoles();

              if (!(roles.contains(Roles.COS_ADMIN) && roles.size() == 1)) {
                return Future.succeededFuture();
              }

              JsonObject emptyUserInfo = new JsonObject();

              return pool.withConnection(
                  conn ->
                      conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                          .execute(Tuple.of(userId, NIL_PHONE, emptyUserInfo))
                          .mapEmpty());
            });

    Future<JsonObject> createClientCreds =
        cosAdminEdgeCase.compose(
            res -> {
              UUID clientId = UUID.randomUUID();

              byte[] randBytes = new byte[CLIENT_SECRET_BYTES];
              randomSource.nextBytes(randBytes);
              String clientSecret = Hex.encodeHexString(randBytes);
              String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);

              Tuple clientTuple = Tuple.of(userId, clientId, hashedClientSecret, DEFAULT_CLIENT);

              JsonObject clientDetails =
                  new JsonObject()
                      .put(RESP_CLIENT_NAME, DEFAULT_CLIENT)
                      .put(RESP_CLIENT_ID, clientId.toString())
                      .put(RESP_CLIENT_SC, clientSecret);

              return pool.withConnection(
                      conn -> conn.preparedQuery(SQL_CREATE_CLIENT).execute(clientTuple))
                  .compose(succ -> Future.succeededFuture(clientDetails));
            });

    createClientCreds
        .onSuccess(
            creds -> {
              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_CREATED_DEFAULT_CLIENT)
                      .status(201)
                      .objectResults(creds)
                      .build();
              promiseHandler.complete(r.toJson());

              LOGGER.info("Created default client credentials for {}", userId);
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
  public Future<JsonObject> searchUser(
      User user, String searchString, Roles role, String resourceServerUrl) {
    LOGGER.debug("Info : {} : Request received", LOGGER.getName());
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

    /* Create error denoting email / userID + role does not exist */
    Supplier<Response> getSearchErr =
        () -> {
          return new ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(ERR_TITLE_USER_NOT_FOUND)
              .status(404)
              .detail(ERR_DETAIL_USER_NOT_FOUND)
              .build();
        };

    Future<JsonObject> foundUser;
    Boolean searchByUserId = searchString.matches(UUID_REGEX);

    if (searchByUserId) {
      foundUser =
          kc.getDetails(List.of(searchString))
              .compose(res -> Future.succeededFuture(res.get(searchString)));
    } else { // search by email
      foundUser = kc.findUserByEmail(searchString);
    }

    Future<UUID> existsReturnUserId =
        foundUser.compose(
            res -> {
              if (res.isEmpty()) {
                return Future.failedFuture(new ComposeException(getSearchErr.get()));
              }

              if (searchByUserId) {
                return Future.succeededFuture(UUID.fromString(searchString));
              } else {
                // userId same as keycloakId
                return Future.succeededFuture(UUID.fromString(res.getString("keycloakId")));
              }
            });

    /*
     * Since you can only search by CONSUMER and PROVIDER roles, we don't
     * need to check the users table for existence and only check if roles.user_id
     * is present */
    Future<Boolean> checkHasRole =
        existsReturnUserId.compose(
            keycloakId ->
                pool.withConnection(
                    conn ->
                        conn.preparedQuery(SQL_CHECK_USER_HAS_PROV_CONS_ROLE_FOR_RS)
                            .execute(Tuple.of(keycloakId, role, resourceServerUrl))
                            .map(row -> row.iterator().next().getBoolean("exists"))));

    checkHasRole
        .compose(
            hasRole -> {
              if (!hasRole) {
                return Future.failedFuture(new ComposeException(getSearchErr.get()));
              }

              JsonObject response = new JsonObject();

              JsonObject userDetails = foundUser.result();

              response.put(RESP_EMAIL, userDetails.getString("email"));
              response.put("userId", existsReturnUserId.result().toString());
              response.put("name", userDetails.getJsonObject("name"));

              return Future.succeededFuture(response);
            })
        .onSuccess(
            res -> {
              Response r =
                  new ResponseBuilder()
                      .type(URN_SUCCESS)
                      .title(SUCC_TITLE_USER_FOUND)
                      .status(200)
                      .objectResults(res)
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
}
