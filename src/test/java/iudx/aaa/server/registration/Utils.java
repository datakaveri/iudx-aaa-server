package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.BCRYPT_LOG_COST;
import static iudx.aaa.server.registration.Constants.BCRYPT_SALT_LEN;
import static iudx.aaa.server.registration.Constants.DEFAULT_CLIENT;
import static iudx.aaa.server.registration.Constants.EMAIL_HASH_ALG;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

/**
 * Class to help manage mock user creation, deletion etc.
 */
public class Utils {

  /**
   * Create a mock user based on the supplied params. The user is created and the information of the
   * user is returned in a JsonObject. The fields returned are: <b>firstName, lastName, orgId, url,
   * email, clientId, clientSecret, keycloakId, userId, phone </b>
   * 
   * @param pool Postgres PgPool connection to make DB calls
   * @param orgId organization ID. If no org is desired, send NIL_UUID
   * @param orgUrl the url of the given orgId. If no org is desired, send empty string
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
      resp.put("url", null);
      email = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";
    } else {
      orgIdToSet = orgId.toString();
      resp.put("orgId", orgId);
      resp.put("url", orgUrl);
      email = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@" + orgUrl;
    }

    resp.put("email", email);

    UUID clientId = UUID.randomUUID();
    UUID clientSecret = UUID.randomUUID();
    UUID keycloakId = UUID.randomUUID();

    resp.put("clientId", clientId.toString());
    resp.put("clientSecret", clientSecret.toString());
    resp.put("keycloakId", keycloakId.toString());

    Promise<UUID> genUserId = Promise.promise();

    Function<String, Tuple> createUserTup = (emailId) -> {
      MessageDigest md = null;
      try {
        md = MessageDigest.getInstance(EMAIL_HASH_ALG);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e.getMessage());
      }

      String hash = DigestUtils.sha1Hex(md.digest(emailId.getBytes()));
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
      String hashedClientSecret =
          OpenBSDBCrypt.generate(clientSecret.toString().toCharArray(), genSalt(), BCRYPT_LOG_COST);

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

  /* Generate salt for bcrypt hash */

  private static byte[] genSalt() {
    SecureRandom random = new SecureRandom();
    byte salt[] = new byte[BCRYPT_SALT_LEN];
    random.nextBytes(salt);
    return salt;
  }

  /**
   * Delete list of fake users from DB. Send list of JsonObjects of user details
   * (strictly userId field must be there in each obj)
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
    pool.withConnection(conn -> conn
        .preparedQuery("DELETE FROM test.users WHERE id = ANY($1::uuid[])").execute(tuple))
        .onSuccess(row -> promise.complete())
        .onFailure(err -> promise.fail("Could not delete users"));

    return promise.future();
  }

}
