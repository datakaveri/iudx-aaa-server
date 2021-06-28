package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.BCRYPT_LOG_COST;
import static iudx.aaa.server.registration.Constants.BCRYPT_SALT_LEN;
import static iudx.aaa.server.registration.Constants.COMPOSE_FAILURE;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.EMAIL_HASH_ALG;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.NIL_UUID;
import static iudx.aaa.server.registration.Constants.NO_ORG_CHECK;
import static iudx.aaa.server.registration.Constants.PROVIDER_PENDING_MESG;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_NAME;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_CLIENT;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_ROLE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_USER;
import static iudx.aaa.server.registration.Constants.SQL_FIND_ORG_BY_ID;
import static iudx.aaa.server.registration.Constants.SQL_FIND_USER_BY_KC_ID;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_CREATED_USER;
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
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.RegistrationRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.User.UserBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
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
            .title(ERR_TITLE_ORG_ID_REQUIRED).stringDetail(ERR_DETAIL_ORG_ID_REQUIRED).build();
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
    /* TODO later on, can check if keycloak ID is not NIL_UUID */
    Future<Integer> checkUserExist =
        pool.withConnection(conn -> conn.preparedQuery(SQL_FIND_USER_BY_KC_ID)
            .execute(Tuple.of(user.getKeycloakId())).map(rows -> rows.size()));

    Future<String> email = kc.getEmailId(user.getKeycloakId());

    Future<String> checkOrgExist;
    String orgIdToSet;

    if (roles.containsKey(Roles.PROVIDER) || roles.containsKey(Roles.DELEGATE)) {
      orgIdToSet = request.getOrgId();
      checkOrgExist = pool.withConnection(
          conn -> conn.preparedQuery(SQL_FIND_ORG_BY_ID).execute(Tuple.of(orgId.toString()))
              .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().getString("url") : null));
    } else {
      checkOrgExist = Future.succeededFuture(NO_ORG_CHECK);
      orgIdToSet = null;
    }

    /* Compose the previous futures to validate. Returns email ID of the user if successful */
    Future<String> validation =
        CompositeFuture.all(checkUserExist, email, checkOrgExist).compose(arr -> {

          int userRow = (int) arr.list().get(0);
          String emailId = (String) arr.list().get(1);
          String url = (String) arr.list().get(2);

          if (userRow != 0) {
            Response r = new ResponseBuilder().status(409).type(URN_ALREADY_EXISTS)
                .title(ERR_TITLE_USER_EXISTS).stringDetail(ERR_DETAIL_USER_EXISTS).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          }

          if (emailId.length() == 0) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_USER_NOT_KC).stringDetail(ERR_DETAIL_USER_NOT_KC).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);
          }

          String emailDomain = emailId.split("@")[1];

          if (url == null) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_ORG_NO_EXIST).stringDetail(ERR_DETAIL_ORG_NO_EXIST).build();
            handler.handle(Future.succeededFuture(r.toJson()));
            return Future.failedFuture(COMPOSE_FAILURE);

          } else if (!url.equals(emailDomain) && !url.equals(NO_ORG_CHECK)) {
            Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(ERR_TITLE_ORG_NO_MATCH).stringDetail(ERR_DETAIL_ORG_NO_MATCH).build();
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
      User u = null;
      u = new UserBuilder().name(user.getName().get("firstName"), user.getName().get("lastName"))
          .roles(rolesForKc).keycloakId(user.getKeycloakId()).userId(userId.result()).build();

      JsonObject clientDetails = new JsonObject().put(RESP_CLIENT_NAME, DEFAULT_CLIENT)
          .put(RESP_CLIENT_ID, clientId.toString()).put(RESP_CLIENT_SC, clientSecret.toString());

      JsonArray clients = new JsonArray().add(clientDetails);
      JsonObject payload =
          u.toJson().put(RESP_CLIENT_ARR, clients).put(RESP_EMAIL, validation.result());
      JsonArray resp = new JsonArray().add(payload);

      String title = SUCC_TITLE_CREATED_USER;
      if (requestedRoles.contains(Roles.PROVIDER)) {
        title = title + PROVIDER_PENDING_MESG;
      }

      Response r = new ResponseBuilder().type(URN_SUCCESS).title(title).status(201)
          .arrayDetail(resp).build();
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
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RegistrationService updateUser(RegistrationRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RegistrationService listOrganization(Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }
}
