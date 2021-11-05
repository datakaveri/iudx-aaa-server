package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.CLIENT_SECRET_BYTES;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.NIL_PHONE;
import static iudx.aaa.server.registration.Constants.NIL_UUID;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_CLIENT;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_ROLE;
import static iudx.aaa.server.registration.Constants.SQL_CREATE_USER;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.Schema;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Class to help manage mock user, organization, delegation creation and deletion etc.
 */
public class Utils {

  private static final Schema DB_SCHEMA = Schema.INSTANCE;

  /* SQL queries for creating and deleting required data */
  public static final String SQL_CREATE_ORG =
      "INSERT INTO " + DB_SCHEMA + ".organizations (name, url, created_at, updated_at) "
          + "VALUES ($1:: text, $2::text, NOW(), NOW()) RETURNING id";

  public static final String SQL_DELETE_USRS =
      "DELETE FROM " + DB_SCHEMA + ".users WHERE organization_id = $1::uuid";

  public static final String SQL_DELETE_ORG =
      "DELETE FROM " + DB_SCHEMA + ".organizations WHERE id = $1::uuid";

  public static final String SQL_DELETE_CONSUMERS =
      "DELETE FROM " + DB_SCHEMA + ".users WHERE email_hash LIKE $1::text || '%'";

  private static final String SQL_DELETE_USER_BY_ID =
      "DELETE FROM " + DB_SCHEMA + ".users WHERE id = ANY($1::uuid[])";

  public static final String SQL_CREATE_ADMIN_SERVER =
      "INSERT INTO " + DB_SCHEMA + ".resource_server (name, owner_id, url, created_at, updated_at) "
          + "VALUES ($1::text, $2::uuid, $3::text, NOW(), NOW())";

  public static final String SQL_DELETE_SERVERS =
      "DELETE FROM " + DB_SCHEMA + ".resource_server WHERE url = ANY ($1::text[])";

  public static final String SQL_DELETE_BULK_ORG =
      "DELETE FROM " + DB_SCHEMA + ".organizations WHERE id = ANY ($1::uuid[])";

  public static final String SQL_GET_SERVER_IDS =
      "SELECT id, url FROM " + DB_SCHEMA + ".resource_server WHERE url = ANY($1::text[])";

  public static final String SQL_CREATE_DELEG = "INSERT INTO " + DB_SCHEMA + ".delegations "
      + "(owner_id, user_id, resource_server_id,status, created_at, updated_at) "
      + "VALUES ($1::uuid, $2::uuid, $3::uuid, $4::" + DB_SCHEMA
      + ".policy_status_enum, NOW(), NOW())" + " RETURNING id, resource_server_id";

  public static final String SQL_GET_DELEG_IDS =
      "SELECT d.id, url FROM " + DB_SCHEMA + ".delegations AS d JOIN " + DB_SCHEMA
          + ".resource_server ON" + " d.resource_server_id = resource_server.id"
          + " WHERE url = ANY($1::text[]) AND d.owner_id = $2::uuid";

  /**
   * Create a mock user based on the supplied params. The user is created and the information of the
   * user is returned in a JsonObject. The fields returned are: <b>firstName, lastName, orgId, url,
   * email, clientId, clientSecret, keycloakId, userId, phone </b>
   * 
   * @param pool Postgres PgPool connection to make DB calls
   * @param orgId organization ID. If no org is desired, send NIL_UUID
   * @param orgUrl the url of the given orgId. Send the URL of the created organization or send an
   *        empty string to get a gmail user. If a proper email is desired, send the url of an
   *        existing organization, but keep orgId as NIL_UUID
   * @param roleMap map of Roles to RoleStatus to set for the user
   * @param needPhone if true, phone number is assigned
   * @return a Future of JsonObject containing user information
   */
  public static Future<JsonObject> createFakeUser(PgPool pool, String orgId, String orgUrl,
      Map<Roles, RoleStatus> roleMap, Boolean needPhone) {

    Promise<JsonObject> response = Promise.promise();
    JsonObject resp = new JsonObject();
    resp.put("firstName", RandomStringUtils.randomAlphabetic(10).toLowerCase());
    resp.put("lastName", RandomStringUtils.randomAlphabetic(10).toLowerCase());

    String email;
    String phone;

    if (needPhone) {
      phone = "9" + RandomStringUtils.randomNumeric(9);
    } else {
      phone = NIL_PHONE;
    }
    resp.put("phone", phone);

    String orgIdToSet;

    if (orgId.toString() == NIL_UUID) {
      orgIdToSet = null;
      resp.put("orgId", null);
    } else {
      orgIdToSet = orgId.toString();
      resp.put("orgId", orgId);
    }

    if (orgUrl == "") {
      resp.put("url", null);
      email = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";
    } /* consumer may want a email with generated domain, but not be associated with an org */
    else {
      resp.put("url", orgUrl);
      email = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@" + orgUrl;
    }

    resp.put("email", email);

    UUID clientId = UUID.randomUUID();
    SecureRandom random = new SecureRandom();
    byte[] randBytes = new byte[CLIENT_SECRET_BYTES];
    random.nextBytes(randBytes);
    String clientSecret = Hex.encodeHexString(randBytes);

    UUID keycloakId = UUID.randomUUID();

    resp.put("clientId", clientId.toString());
    resp.put("clientSecret", clientSecret.toString());
    resp.put("keycloakId", keycloakId.toString());

    Promise<UUID> genUserId = Promise.promise();

    Function<String, Tuple> createUserTup = (emailId) -> {
      String hash = DigestUtils.sha1Hex(emailId.getBytes());
      String emailHash = emailId.split("@")[1] + '/' + hash;
      return Tuple.of(phone, orgIdToSet, emailHash, keycloakId);
    };

    /*
     * Function to complete generated User ID promise and to create list of tuples for role creation
     * batch query
     */
    Function<UUID, List<Tuple>> createRoleTup = (id) -> {
      genUserId.complete(id);
      return roleMap.entrySet().stream()
          .map(p -> Tuple.of(id, p.getKey().name(), p.getValue().name()))
          .collect(Collectors.toList());
    };

    /* Function to hash client secret, and create tuple for client creation query */
    Supplier<Tuple> createClientTup = () -> {
      String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);

      return Tuple.of(genUserId.future().result(), clientId, hashedClientSecret, DEFAULT_CLIENT);
    };

    pool.withTransaction(
        conn -> conn.preparedQuery(SQL_CREATE_USER).execute(createUserTup.apply(email))
            .map(rows -> rows.iterator().next().getUUID("id")).map(uid -> createRoleTup.apply(uid))
            .compose(roleDetails -> conn.preparedQuery(SQL_CREATE_ROLE).executeBatch(roleDetails))
            .map(success -> createClientTup.get())
            .compose(clientDetails -> conn.preparedQuery(SQL_CREATE_CLIENT).execute(clientDetails)))
        .onSuccess(row -> {
          resp.put("userId", genUserId.future().result());
          response.complete(resp);
        }).onFailure(res -> response.fail("Failed to create fake user" + res.getMessage()));

    return response.future();
  }

  /**
   * Delete list of fake users from DB. Send list of JsonObjects of user details (strictly userId
   * field must be there in each obj)
   * 
   * @param pool Postgres PgPool connection to make DB calls
   * @param userList list of JsonObjects of users to delete
   * @return Void future indicating success or failure
   */
  public static Future<Void> deleteFakeUser(PgPool pool, List<JsonObject> userList) {
    Promise<Void> promise = Promise.promise();

    List<UUID> ids = userList.stream().map(obj -> UUID.fromString(obj.getString("userId")))
        .collect(Collectors.toList());

    Tuple tuple = Tuple.of(ids.toArray(UUID[]::new));
    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_USER_BY_ID).execute(tuple))
        .onSuccess(row -> promise.complete())
        .onFailure(err -> promise.fail("Could not delete users"));

    return promise.future();
  }

}
