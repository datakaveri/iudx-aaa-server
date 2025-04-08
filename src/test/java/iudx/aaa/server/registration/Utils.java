package iudx.aaa.server.registration;

import static iudx.aaa.server.admin.Constants.SQL_CREATE_RS_IF_NOT_EXIST;
import static iudx.aaa.server.policy.Constants.TEST_INSERT_DELEGATION;
import static iudx.aaa.server.registration.Constants.CLIENT_SECRET_BYTES;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.NIL_PHONE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_CLIENT;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_USER_IF_NOT_EXISTS;
import static iudx.aaa.server.registration.Constants.TEST_SQL_CREATE_ROLE_WITH_ID;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.models.ApdStatus;
import iudx.aaa.server.apiserver.models.DelegationStatus;
import iudx.aaa.server.apiserver.models.RoleStatus;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.models.User;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Class to help create fake data and store it so that it's easily accessible. Used in unit tests
 */
public class Utils {

  /* SQL queries for creating and deleting required data */
  public static final String SQL_DELETE_ROLES_OF_RS =
      "DELETE FROM roles WHERE resource_server_id = ANY($1::uuid[])";

  public static final String SQL_CREATE_APD =
      "INSERT INTO apds (name, owner_id, url, status, created_at, updated_at) VALUES "
          + "($1::text, $2::uuid, $3::text, $4::apd_status_enum, NOW(), NOW()) RETURNING id";

  public static final String SQL_DELETE_APD =
      "UPDATE apds SET status ='INACTIVE' where id = ANY($1::uuid[])";

  private static final String SQL_DELETE_USER_BY_ID =
      "DELETE FROM users WHERE id = ANY($1::uuid[])";

  public static final String SQL_DELETE_RESOURCE_SERVER =
      "DELETE FROM resource_server WHERE id = ANY($1::uuid[])";

  public static final String SQL_DELETE_DELEGATE =
      "UPDATE delegations SET status ='DELETED' where id = ANY($1::uuid[])";

  PgPool pool;
  Map<String, UUID> resourceServerMap = new HashMap<String, UUID>();
  List<UUID> delegationsList = new ArrayList<UUID>();
  public Map<String, UUID> apdMap = new HashMap<String, UUID>();
  private Map<UUID, FakeUserDetails> userMap = new HashMap<UUID, FakeUserDetails>();

  private SecureRandom randomSource;

  public Utils(PgPool pool) {
    this.pool = pool;
    randomSource = new SecureRandom();
  }

  /**
   * Get user details from user's {@link FakeUserDetails} object in {@link #userMap}
   *
   * @param user user object
   * @return
   */
  public FakeUserDetails getDetails(User user) {
    return userMap.get(UUID.fromString(user.getUserId()));
  }

  /**
   * Get JSON given by most {@link KcAdmin} methods for a particular user.
   *
   * @param user
   * @return
   */
  public JsonObject getKcAdminJson(User user) {
    UUID userId = UUID.fromString(user.getUserId());
    return new JsonObject()
        .put("keycloakId", userId.toString())
        .put("email", userMap.get(userId).email)
        .put("name", user.toJson().getJsonObject("name"));
  }

  /**
   * Delete fake users created by {@link Utils}
   *
   * @return Void future indicating success or failure
   */
  public Future<Void> deleteFakeUser() {
    Promise<Void> promise = Promise.promise();

    UUID[] ids = userMap.keySet().toArray(UUID[]::new);

    Tuple tuple = Tuple.of(ids);
    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_USER_BY_ID).execute(tuple))
        .onSuccess(row -> promise.complete())
        .onFailure(err -> promise.fail("Could not delete users"));

    return promise.future();
  }

  /**
   * Create a mock resource server based on URL and admin user. The admin user is created if it not
   * exists.
   *
   * @param url URL of the server to be created
   * @param admin admin user
   * @return Void future indicating success or failure
   */
  public Future<Void> createFakeResourceServer(String url, User admin) {
    Promise<Void> response = Promise.promise();

    Tuple rsTuple = Tuple.of(RandomStringUtils.randomAlphabetic(10), url, admin.getUserId());

    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                    .execute(Tuple.of(admin.getUserId(), NIL_PHONE, new JsonObject()))
                    .compose(res -> conn.preparedQuery(SQL_CREATE_RS_IF_NOT_EXIST).execute(rsTuple))
                    .compose(
                        rows -> {
                          resourceServerMap.put(url, rows.iterator().next().getUUID("id"));

                          if (!userMap.containsKey(UUID.fromString(admin.getUserId()))) {
                            userMap.put(UUID.fromString(admin.getUserId()), new FakeUserDetails());
                          }
                          return Future.succeededFuture();
                        }))
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Fake RS creation error " + fail.toString()));

    return response.future();
  }

  /**
   * Deletes resourceServers that are added using {@link #createFakeResourceServer(String, User)}
   *
   * @return Void future indicating success or failure
   */
  public Future<Void> deleteFakeResourceServer() {
    Promise<Void> response = Promise.promise();

    List<UUID> ids =
        resourceServerMap.entrySet().stream()
            .map(obj -> obj.getValue())
            .collect(Collectors.toList());

    Tuple tuple = Tuple.of(ids.toArray(UUID[]::new));
    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_DELETE_ROLES_OF_RS)
                    .execute(tuple)
                    .compose(res -> conn.preparedQuery(SQL_DELETE_RESOURCE_SERVER).execute(tuple)))
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Db failure: " + fail.toString()));
    return response.future();
  }

  /**
   * create delegations for users
   *
   * @param pool Postgres PgPool connection to make DB calls
   * @param userId UUID of user for whom delegation is to be written
   * @param ownerId UUID of user who is creating the delegation
   * @param itemId UUID of the id of the resource_server on which delegation is to be made
   * @return Void future indicating success or failure
   */
  public Future<Void> createFakeDelegation(
      UUID id, User delegator, User delegate, String rsUrl, Roles role, DelegationStatus status) {
    Promise<Void> response = Promise.promise();

    UUID roleId = userMap.get(UUID.fromString(delegator.getUserId())).getRoleId(role, rsUrl);
    if (roleId == null) {
      response.fail("The delegator does not have the requested role for requested RS");
      return response.future();
    }

    Tuple tuple = Tuple.of(id, delegate.getUserId(), roleId, status);

    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                    .execute(Tuple.of(delegate.getUserId(), NIL_PHONE, new JsonObject()))
                    .compose(res -> conn.preparedQuery(TEST_INSERT_DELEGATION).execute(tuple))
                    .compose(
                        rows -> {
                          delegationsList.add(id);

                          if (!userMap.containsKey(UUID.fromString(delegate.getUserId()))) {
                            userMap.put(
                                UUID.fromString(delegate.getUserId()), new FakeUserDetails());
                          }
                          return Future.succeededFuture();
                        }))
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Fake delegation creation error " + fail.toString()));

    return response.future();
  }

  public Future<Void> deleteFakeDelegation() {
    Promise<Void> response = Promise.promise();

    Tuple tuple = Tuple.of(delegationsList.toArray(UUID[]::new));
    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_DELEGATE).execute(tuple))
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Db failure: " + fail.toString()));
    return response.future();
  }

  /**
   * Create a mock user in the database based on the {@link User} object passed. Consumer and
   * provider roles in the approved state are added if contained in the {@link User} object. The
   * resource servers associated with these roles must be created beforehand. Extra user information
   * is added to the {@link #userMap} in the form of {@link FakeUserDetails}.
   *
   * @param user the user to be created in the DB
   * @param needPhone if true, phone number is assigned
   * @param needUserInfo if true, some random JsonObject is created, else is empty
   * @return a Future of JsonObject containing user information
   */
  public Future<Void> createFakeUser(User user, Boolean needPhone, Boolean needUserInfo) {

    Promise<Void> response = Promise.promise();

    String phone;
    JsonObject userInfo;

    if (needPhone) {
      phone = "9" + RandomStringUtils.randomNumeric(9);
    } else {
      phone = NIL_PHONE;
    }

    if (needUserInfo) {
      userInfo =
          new JsonObject()
              .put(RandomStringUtils.randomAlphabetic(10), RandomStringUtils.randomAlphabetic(10));
    } else {
      userInfo = new JsonObject();
    }

    FakeUserDetails details = new FakeUserDetails(phone, userInfo);

    List<Tuple> roleTuple = new ArrayList<Tuple>();
    user.getRolesToRsMapping()
        .forEach(
            (roleName, rsUrlArr) -> {
              Roles role = Roles.valueOf(roleName.toUpperCase());
              if (!(role.equals(Roles.CONSUMER) || role.equals(Roles.PROVIDER))) {
                return;
              }
              rsUrlArr.forEach(
                  urlObj -> {
                    UUID roleId = UUID.randomUUID();
                    String url = (String) urlObj;

                    roleTuple.add(
                        Tuple.of(
                            roleId,
                            user.getUserId(),
                            role,
                            resourceServerMap.get(url),
                            RoleStatus.APPROVED));
                    details.addRoleId(role, url, roleId);
                  });
            });

    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                    .execute(Tuple.of(user.getUserId(), phone, userInfo))
                    .compose(
                        roleDetails -> {
                          if (!roleTuple.isEmpty()) {
                            return conn.preparedQuery(TEST_SQL_CREATE_ROLE_WITH_ID)
                                .executeBatch(roleTuple);
                          } else return Future.succeededFuture();
                        }))
        .onSuccess(
            row -> {
              userMap.put(UUID.fromString(user.getUserId()), details);
              response.complete();
            })
        .onFailure(
            res -> {
              response.fail("Failed to create fake user" + res.getMessage());
            });

    return response.future();
  }

  /**
   * Create a mock APD based on URL and trustee user. The trustee user is created if it not exists.
   *
   * @param url URL of the APD to be created
   * @param trustee trustee user
   * @return Void future indicating success or failure
   */
  public Future<Void> createFakeApd(String url, User trustee, ApdStatus status) {
    Promise<Void> response = Promise.promise();

    Tuple apdTuple = Tuple.of(url + "name", trustee.getUserId(), url, status);

    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_CREATE_USER_IF_NOT_EXISTS)
                    .execute(Tuple.of(trustee.getUserId(), NIL_PHONE, new JsonObject()))
                    .compose(res -> conn.preparedQuery(SQL_CREATE_APD).execute(apdTuple)))
        .compose(
            rows -> {
              apdMap.put(url, rows.iterator().next().getUUID("id"));
              if (!userMap.containsKey(UUID.fromString(trustee.getUserId()))) {
                userMap.put(UUID.fromString(trustee.getUserId()), new FakeUserDetails());
              }
              return Future.succeededFuture();
            })
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Fake APD creation error " + fail.toString()));

    return response.future();
  }

  /**
   * Deletes APDs that are added using {@link #createFakeApd(String, User)}
   *
   * @return Void future indicating success or failure
   */
  public Future<Void> deleteFakeApd() {
    Promise<Void> response = Promise.promise();

    List<UUID> ids =
        apdMap.entrySet().stream().map(obj -> obj.getValue()).collect(Collectors.toList());

    Tuple tuple = Tuple.of(ids.toArray(UUID[]::new));
    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_APD).execute(tuple))
        .onSuccess(succ -> response.complete())
        .onFailure(fail -> response.fail("Db failure: " + fail.toString()));
    return response.future();
  }

  public Future<Void> createClientCreds(User user) {
    Promise<Void> response = Promise.promise();
    UUID clientId = UUID.randomUUID();
    byte[] randBytes = new byte[CLIENT_SECRET_BYTES];
    randomSource.nextBytes(randBytes);
    String clientSecret = Hex.encodeHexString(randBytes);
    String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);
    Tuple clientTuple = Tuple.of(user.getUserId(), clientId, hashedClientSecret, DEFAULT_CLIENT);

    pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_CLIENT).execute(clientTuple))
        .compose(
            res -> {
              FakeUserDetails details = this.getDetails(user);
              details.clientId = clientId.toString();
              details.clientSecret = clientSecret;

              userMap.put(UUID.fromString(user.getUserId()), details);
              return Future.succeededFuture();
            })
        .onSuccess(res -> response.complete())
        .onFailure(
            fail -> {
              response.fail("Failed client creation");
            });

    return response.future();
  }

  public Future<Void> addProviderStatusRole(
      User user, String rsUrl, RoleStatus status, UUID roleId) {
    Promise<Void> response = Promise.promise();

    Tuple tup =
        Tuple.of(roleId, user.getUserId(), Roles.PROVIDER, resourceServerMap.get(rsUrl), status);

    pool.withConnection(conn -> conn.preparedQuery(TEST_SQL_CREATE_ROLE_WITH_ID).execute(tup))
        .compose(
            res -> {
              return Future.succeededFuture();
            })
        .onSuccess(res -> response.complete())
        .onFailure(
            fail -> {
              response.fail("Failed role creation");
            });

    return response.future();
  }
}
