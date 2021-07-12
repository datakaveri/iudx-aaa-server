package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.BCRYPT_LOG_COST;
import static iudx.aaa.server.registration.Constants.BCRYPT_SALT_LEN;
import static iudx.aaa.server.registration.Constants.COMPOSE_FAILURE;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.EMAIL_HASH_ALG;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ROLE_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ROLE_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.NIL_PHONE;
import static iudx.aaa.server.registration.Constants.NIL_UUID;
import static iudx.aaa.server.registration.Constants.NO_ORG_CHECK;
import static iudx.aaa.server.registration.Constants.PROVIDER_PENDING_MESG;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_NAME;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.RESP_ORG;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_CLIENT;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_ROLE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_USER;
import static iudx.aaa.server.registration.Constants.SQL_FIND_ORG_BY_ID;
import static iudx.aaa.server.registration.Constants.SQL_FIND_USER_BY_KC_ID;
import static iudx.aaa.server.registration.Constants.SQL_GET_ALL_ORGS;
import static iudx.aaa.server.registration.Constants.SQL_GET_CLIENTS_FORMATTED;
import static iudx.aaa.server.registration.Constants.SQL_GET_ORG_DETAILS;
import static iudx.aaa.server.registration.Constants.SQL_GET_PHONE_JOIN_ORG;
import static iudx.aaa.server.registration.Constants.SQL_GET_REG_ROLES;
import static iudx.aaa.server.registration.Constants.SQL_UPDATE_ORG_ID;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_CREATED_USER;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_ORG_READ;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_UPDATED_USER_ROLES;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_READ;
import static iudx.aaa.server.registration.Constants.URN_ALREADY_EXISTS;
import static iudx.aaa.server.registration.Constants.URN_INVALID_INPUT;
import static iudx.aaa.server.registration.Constants.URN_MISSING_INFO;
import static iudx.aaa.server.registration.Constants.URN_SUCCESS;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.RegistrationRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdateProfileRequest;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

/**
 * The Registration Service Implementation.
 * <h1>Registration Service Implementation</h1>
 * <p>
 * The Registration Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.registration.RegistrationService}.
 * </p>
 * 
 */

public class RegistrationServiceImpl implements RegistrationService {

  private static final Logger LOGGER = LogManager.getLogger(RegistrationServiceImpl.class);

  private PgPool pool;
  private KcAdmin kc;

  public RegistrationServiceImpl(PgPool pool, KcAdmin kc) {
    this.pool = pool;
    this.kc = kc;
  }

  @Override
  public RegistrationService createUser(RegistrationRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    List<Roles> requestedRoles = request.getRoles();
    UUID orgId = UUID.fromString(request.getOrgId());
    final String phone = request.getPhone();

    if (requestedRoles.contains(Roles.PROVIDER) || requestedRoles.contains(Roles.DELEGATE)) {
      if (orgId.toString().equals(NIL_UUID)) {
        Response r = new ResponseBuilder().status(400).type(URN_MISSING_INFO)
            .title(ERR_TITLE_ORG_ID_REQUIRED).detail(ERR_DETAIL_ORG_ID_REQUIRED).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return this;
      }
    }

    Map<Roles, RoleStatus> roles = new HashMap<Roles, RoleStatus>();

    for (Roles r : requestedRoles) {
      if (r == Roles.PROVIDER) {
        roles.put(r, RoleStatus.PENDING);
      } else {
        roles.put(r, RoleStatus.APPROVED);
      }
    }
    /* TODO later on, can check if user ID is not NIL_UUID */
    Future<Integer> checkUserExist =
        pool.withConnection(conn -> conn.preparedQuery(SQL_FIND_USER_BY_KC_ID)
            .execute(Tuple.of(user.getKeycloakId())).map(rows -> rows.size()));

    Future<String> email = kc.getEmailId(user.getKeycloakId());

    Future<String> checkOrgExist;
    String orgIdToSet;

    if (roles.containsKey(Roles.PROVIDER) || roles.containsKey(Roles.DELEGATE)) {
      orgIdToSet = request.getOrgId();
      checkOrgExist = pool.withConnection(
          conn -> conn.preparedQuery(SQL_GET_ORG_DETAILS).execute(Tuple.of(orgId.toString())).map(
              rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson().toString() : null));
    } else {
      checkOrgExist = Future.succeededFuture(NO_ORG_CHECK);
      orgIdToSet = null;
    }

    /* Compose the previous futures to validate. Returns email ID of the user if successful */
    Future<String> validation =
        CompositeFuture.all(checkUserExist, email, checkOrgExist).compose(arr -> {

          int userRow = (int) arr.list().get(0);
          String emailId = (String) arr.list().get(1);
          String orgDetails = (String) arr.list().get(2);

          if (userRow != 0) {
            Response r = new ResponseBuilder().status(409).type(URN_ALREADY_EXISTS)
                .title(ERR_TITLE_USER_EXISTS).detail(ERR_DETAIL_USER_EXISTS).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          }

          if (emailId.length() == 0) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_USER_NOT_KC).detail(ERR_DETAIL_USER_NOT_KC).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          }

          String emailDomain = emailId.split("@")[1];

          if (orgDetails == null) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_ORG_NO_EXIST).detail(ERR_DETAIL_ORG_NO_EXIST).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          } else if (orgDetails == NO_ORG_CHECK) {
            return Future.succeededFuture(emailId);
          }

          String url = new JsonObject(orgDetails).getString("url");

          if (!url.equals(emailDomain)) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_ORG_NO_MATCH).detail(ERR_DETAIL_ORG_NO_MATCH).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          }

          return Future.succeededFuture(emailId);
        });

    UUID clientId = UUID.randomUUID();
    UUID clientSecret = UUID.randomUUID();

    List<Roles> rolesForKc =
        requestedRoles.stream().filter(x -> x != Roles.PROVIDER).collect(Collectors.toList());

    Promise<UUID> genUserId = Promise.promise();
    Future<UUID> userId = genUserId.future();

    /*
     * Function to form tuple for create user query. The email ID of the user is taken as input
     */
    Function<String, Tuple> createUserTup = (emailId) -> {
      MessageDigest md = null;
      try {
        md = MessageDigest.getInstance(EMAIL_HASH_ALG);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e.getMessage());
      }

      String hash = DigestUtils.sha1Hex(md.digest(emailId.getBytes()));
      String emailHash = emailId.split("@")[1] + '/' + hash;
      return Tuple.of(phone, orgIdToSet, emailHash, user.getKeycloakId());
    };

    /*
     * Function to complete generated User ID promise and to create list of tuples for role creation
     * batch query
     */
    Function<UUID, List<Tuple>> createRoleTup = (id) -> {
      genUserId.complete(id);
      return roles.entrySet().stream()
          .map(p -> Tuple.of(id, p.getKey().name(), p.getValue().name()))
          .collect(Collectors.toList());
    };

    /* Function to hash client secret, and create tuple for client creation query */
    Supplier<Tuple> createClientTup = () -> {
      String hashedClientSecret =
          OpenBSDBCrypt.generate(clientSecret.toString().toCharArray(), genSalt(), BCRYPT_LOG_COST);

      return Tuple.of(userId.result(), clientId, hashedClientSecret, DEFAULT_CLIENT);
    };

    /* Insertion into users, roles, clients tables and add roles to Keycloak */
    Future<Void> query = validation.compose(emailId -> pool.withTransaction(
        conn -> conn.preparedQuery(SQL_CREATE_USER).execute(createUserTup.apply(emailId))
            .map(rows -> rows.iterator().next().getUUID("id")).map(uid -> createRoleTup.apply(uid))
            .compose(roleDetails -> conn.preparedQuery(SQL_CREATE_ROLE).executeBatch(roleDetails))
            .map(success -> createClientTup.get())
            .compose(clientDetails -> conn.preparedQuery(SQL_CREATE_CLIENT).execute(clientDetails))
            .compose(success -> kc.modifyRoles(user.getKeycloakId(), rolesForKc))));

    query.onSuccess(success -> {
      User u =
          new UserBuilder().name(user.getName().get("firstName"), user.getName().get("lastName"))
              .roles(rolesForKc).keycloakId(user.getKeycloakId()).userId(userId.result()).build();

      JsonObject clientDetails = new JsonObject().put(RESP_CLIENT_NAME, DEFAULT_CLIENT)
          .put(RESP_CLIENT_ID, clientId.toString()).put(RESP_CLIENT_SC, clientSecret.toString());

      JsonArray clients = new JsonArray().add(clientDetails);
      JsonObject payload =
          u.toJson().put(RESP_CLIENT_ARR, clients).put(RESP_EMAIL, validation.result());

      if (phone != NIL_PHONE) {
        payload.put(RESP_PHONE, phone);
      }

      if (checkOrgExist.result() != NO_ORG_CHECK) {
        payload.put(RESP_ORG, new JsonObject(checkOrgExist.result()));
      }

      String title = SUCC_TITLE_CREATED_USER;
      if (requestedRoles.contains(Roles.PROVIDER)) {
        title = title + PROVIDER_PENDING_MESG;
      }

      Response r = new ResponseBuilder().type(URN_SUCCESS).title(title).status(201)
          .objectResults(payload).build();
      handler.handle(Future.succeededFuture(r.toJson()));

      LOGGER.info("Created user profile for " + userId.result());
    }).onFailure(e -> {
      if (e.getMessage().equals(COMPOSE_FAILURE)) {
        return; // do nothing
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });

    return this;
  }

  /* Generate salt for bcrypt hash */
  private byte[] genSalt() {
    SecureRandom random = new SecureRandom();
    byte salt[] = new byte[BCRYPT_SALT_LEN];
    random.nextBytes(salt);
    return salt;
  }

  @Override
  public RegistrationService listUser(User user, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Future<JsonObject> phoneOrgDetails =
        pool.withConnection(conn -> conn.preparedQuery(SQL_GET_PHONE_JOIN_ORG)
            .execute(Tuple.of(user.getUserId())).map(rows -> rows.iterator().next().toJson()));

    Future<String> email = kc.getEmailId(user.getKeycloakId());

    Collector<Row, ?, List<JsonObject>> clientDetails =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> clientQuery =
        pool.withConnection(conn -> conn.preparedQuery(SQL_GET_CLIENTS_FORMATTED)
            .collecting(clientDetails).execute(Tuple.of(user.getUserId())).map(res -> res.value()));

    CompositeFuture.all(phoneOrgDetails, clientQuery, email).onSuccess(obj -> {

      JsonObject details = (JsonObject) obj.list().get(0);
      @SuppressWarnings("unchecked")
      List<JsonObject> clients = (List<JsonObject>) obj.list().get(1);
      String emailId = (String) obj.list().get(2);

      if (emailId.length() == 0) {
        Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(ERR_TITLE_USER_NOT_KC).detail(ERR_DETAIL_USER_NOT_KC).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return;
      }

      JsonObject response = user.toJson();
      response.put(RESP_EMAIL, emailId);
      response.put(RESP_CLIENT_ARR, new JsonArray(clients));

      String phone = (String) details.remove("phone");
      if (!phone.equals(NIL_PHONE)) {
        response.put(RESP_PHONE, phone);
      }

      /* details will have only org details or or only null */
      if (details.getString("url") != null) {
        response.put(RESP_ORG, details);
      }

      Response r = new ResponseBuilder().type(URN_SUCCESS).title(SUCC_TITLE_USER_READ).status(200)
          .objectResults(response).build();
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
  public RegistrationService updateUser(UpdateProfileRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    List<Roles> requestedRoles = request.getRoles();
    String orgId = request.getOrgId();

    Map<Roles, RoleStatus> roles = new HashMap<Roles, RoleStatus>();

    for (Roles r : requestedRoles) {
      if (r == Roles.PROVIDER) {
        roles.put(r, RoleStatus.PENDING);
      } else {
        roles.put(r, RoleStatus.APPROVED);
      }
    }

    Collector<Row, ?, List<Roles>> roleCollect =
        Collectors.mapping(row -> row.get(Roles.class, "role"), Collectors.toList());

    Future<List<Roles>> getRegisteredRoles =
        pool.withConnection(conn -> conn.preparedQuery(SQL_GET_REG_ROLES).collecting(roleCollect)
            .execute(Tuple.of(user.getUserId())).map(rows -> rows.value()));

    Future<String> checkOrgRequired = getRegisteredRoles.compose(registeredRoles -> {

      List<Roles> duplicate =
          registeredRoles.stream().filter(requestedRoles::contains).collect(Collectors.toList());

      if (duplicate.size() != 0) {
        String dupRoles = duplicate.stream().map(str -> str.name().toLowerCase())
            .collect(Collectors.joining(", "));

        Response r = new ResponseBuilder().status(400).type(URN_ALREADY_EXISTS)
            .title(ERR_TITLE_ROLE_EXISTS).detail(ERR_DETAIL_ROLE_EXISTS + dupRoles).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }

      /* If already registered as provider/delegate do not check org ID */
      if (registeredRoles.contains(Roles.PROVIDER) || registeredRoles.contains(Roles.DELEGATE)) {
        return Future.succeededFuture(NO_ORG_CHECK);
      }

      /* If consumer and to add role of provider/delegate, orgId required */
      if (requestedRoles.contains(Roles.PROVIDER) || requestedRoles.contains(Roles.DELEGATE)) {
        if (orgId.toString().equals(NIL_UUID)) {
          Response r = new ResponseBuilder().status(400).type(URN_MISSING_INFO)
              .title(ERR_TITLE_ORG_ID_REQUIRED).detail(ERR_DETAIL_ORG_ID_REQUIRED).build();
          handler.handle(Future.succeededFuture(r.toJson()));
          return Future.failedFuture(COMPOSE_FAILURE);
        }
      }

      return pool.withConnection(
          conn -> conn.preparedQuery(SQL_FIND_ORG_BY_ID).execute(Tuple.of(orgId.toString())).map(
              rows -> rows.iterator().hasNext() ? rows.iterator().next().getString("url") : null));
    });

    Future<String> email = kc.getEmailId(user.getKeycloakId());

    Future<Void> validateOrg = CompositeFuture.all(checkOrgRequired, email).compose(x -> {
      String url = (String) x.list().get(0);
      String emailId = (String) x.list().get(1);

      String emailDomain = emailId.split("@")[1];

      if (emailId.length() == 0) {
        Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(ERR_TITLE_USER_NOT_KC).detail(ERR_DETAIL_USER_NOT_KC).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }

      if (url == null) {
        Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(ERR_TITLE_ORG_NO_EXIST).detail(ERR_DETAIL_ORG_NO_EXIST).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);

      } else if (!url.equals(emailDomain) && !url.equals(NO_ORG_CHECK)) {
        Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(ERR_TITLE_ORG_NO_MATCH).detail(ERR_DETAIL_ORG_NO_MATCH).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return Future.failedFuture(COMPOSE_FAILURE);
      }

      return Future.succeededFuture();
    });

    List<Roles> rolesForKc =
        requestedRoles.stream().filter(x -> x != Roles.PROVIDER).collect(Collectors.toList());

    List<Tuple> roleDetails = roles.entrySet().stream()
        .map(p -> Tuple.of(user.getUserId(), p.getKey().name(), p.getValue().name()))
        .collect(Collectors.toList());

    /* supplier to create tuple for org update */
    Supplier<Tuple> updateOrgIdTup = () -> {
      if (checkOrgRequired.result() == NO_ORG_CHECK) {
        return Tuple.of(null, user.getUserId());
      }
      return Tuple.of(request.getOrgId(), user.getUserId());
    };

    Future<Void> query = validateOrg.compose(res -> pool
        .withTransaction(conn -> conn.preparedQuery(SQL_CREATE_ROLE).executeBatch(roleDetails)
            .compose(success -> conn.preparedQuery(SQL_UPDATE_ORG_ID).execute(updateOrgIdTup.get()))
            .compose(success -> kc.modifyRoles(user.getKeycloakId(), rolesForKc))));

    /* After successful insertion, get user details for response */
    Future<JsonObject> phoneOrgDetails =
        query.compose(x -> pool.withConnection(conn -> conn.preparedQuery(SQL_GET_PHONE_JOIN_ORG)
            .execute(Tuple.of(user.getUserId())).map(rows -> rows.iterator().next().toJson())));

    Collector<Row, ?, List<JsonObject>> clientDetails =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    Future<List<JsonObject>> clientQuery = query.compose(x -> pool.withConnection(
        conn -> conn.preparedQuery(SQL_GET_CLIENTS_FORMATTED).collecting(clientDetails)
            .execute(Tuple.of(user.getUserId())).map(res -> res.value())));

    CompositeFuture.all(phoneOrgDetails, clientQuery).onSuccess(obj -> {
      JsonObject details = (JsonObject) obj.list().get(0);
      @SuppressWarnings("unchecked")
      List<JsonObject> clients = (List<JsonObject>) obj.list().get(1);

      List<Roles> approvedRoles = new ArrayList<Roles>();
      approvedRoles.addAll(user.getRoles());
      approvedRoles.addAll(rolesForKc);

      User u = new UserBuilder()
          .name(user.getName().get("firstName"), user.getName().get("lastName"))
          .roles(approvedRoles).keycloakId(user.getKeycloakId()).userId(user.getUserId()).build();

      JsonObject response = u.toJson();
      response.put(RESP_EMAIL, email.result());
      response.put(RESP_CLIENT_ARR, new JsonArray(clients));

      String phone = (String) details.remove("phone");
      if (!phone.equals(NIL_PHONE)) {
        response.put(RESP_PHONE, phone);
      }

      /* details will have only org details or or only null */
      if (details.getString("url") != null) {
        response.put(RESP_ORG, details);
      }

      String title = SUCC_TITLE_UPDATED_USER_ROLES;
      if (requestedRoles.contains(Roles.PROVIDER)) {
        title = title + PROVIDER_PENDING_MESG;
      }

      Response r = new ResponseBuilder().type(URN_SUCCESS).title(title).status(200)
          .objectResults(response).build();
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
  public RegistrationService listOrganization(Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    Collector<Row, ?, List<JsonObject>> orgCollect =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    pool.withConnection(conn -> conn.preparedQuery(SQL_GET_ALL_ORGS).collecting(orgCollect)
        .execute().map(rows -> rows.value()).onSuccess(obj -> {
          JsonArray resp = new JsonArray(obj);

          Response r = new ResponseBuilder().type(URN_SUCCESS).title(SUCC_TITLE_ORG_READ)
              .status(200).arrayResults(resp).build();
          handler.handle(Future.succeededFuture(r.toJson()));
        }).onFailure(e -> {
          LOGGER.error(e.getMessage());
          handler.handle(Future.failedFuture("Internal error"));
        }));

    return this;
  }
}
