package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.BAD_REQUEST;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECKUSEREXIST;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_APD_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_RES_SER;
import static iudx.aaa.server.policy.Constants.CHECK_TRUSTEE_POLICY;
import static iudx.aaa.server.policy.Constants.DUPLICATE_POLICY;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.INVALID_DATETIME;
import static iudx.aaa.server.policy.Constants.INVALID_USER;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.NO_AUTH_TRUSTEE_POLICY;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_TABLE;
import static iudx.aaa.server.policy.Constants.SERVER_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.VALIDATE_EXPIRY_FAIL;
import static iudx.aaa.server.policy.Constants.itemTypes;
import static iudx.aaa.server.policy.Constants.status;

public class createPolicy {
  private static final Logger LOGGER = LogManager.getLogger(createPolicy.class);

  private final PgPool pool;
  private final JsonObject options;

  public createPolicy(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }

  /**
   * Validate expiry time
   *
   * @param req - set of expiryTime
   * @return
   */
  public Future<Void> validateExpiry(List<String> req) {
    Promise<Void> p = Promise.promise();
    try {

      for (String obj : req) {
        LocalDateTime expTime = LocalDateTime.parse(obj, DateTimeFormatter.ISO_DATE_TIME);
        if (expTime.compareTo(LocalDateTime.now(ZoneOffset.UTC)) < 0) {
          Response r =
              new Response.ResponseBuilder()
                  .type(URN_INVALID_INPUT)
                  .title(VALIDATE_EXPIRY_FAIL)
                  .detail(expTime.toString())
                  .status(400)
                  .build();
          p.fail(new ComposeException(r));
          break;
        }
      }

    } catch (DateTimeParseException e) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(INVALID_DATETIME)
              .detail(e.getParsedString())
              .status(400)
              .build();
      p.fail(new ComposeException(r));
    } catch (Exception e) {
      p.fail(INTERNALERROR);
    }
    if (!p.future().failed()) p.complete();
    return p.future();
  }

  /**
   * Check if the users exist in users table
   *
   * @param users - set of userId
   * @return Set<UUID> -> set of uuids not present in the db
   */
  public Future<Set<UUID>> checkUserExist(Set<UUID> users) {

    Promise<Set<UUID>> p = Promise.promise();

    if (users.isEmpty()) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(BAD_REQUEST)
              .detail(BAD_REQUEST)
              .status(400)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    Collector<Row, ?, Set<UUID>> userIdCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toSet());

    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECKUSEREXIST)
                .collecting(userIdCollector)
                .execute(Tuple.of(users.toArray(UUID[]::new)))
                .onFailure(
                    obj -> {
                      LOGGER.error("checkUserExist db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.value().isEmpty()) {
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_INVALID_INPUT)
                                .title(INVALID_USER)
                                .detail(users.toString())
                                .status(400)
                                .build();
                        p.fail(new ComposeException(r));
                      } else {
                        Set<UUID> resp = users;
                        resp.removeAll(obj.value());
                        p.complete(resp);
                      }
                    }));
    return p.future();
  }

  /**
   * Insert into policy table or the apd_policies table
   *
   * @param query - query to insert
   * @param tup - list of tuples
   * @return Boolean - true if insertion is true
   */
  public Future<Boolean> insertPolicy(String query, List<Tuple> tup) {
    Promise<Boolean> p = Promise.promise();
    pool.withTransaction(conn -> conn.preparedQuery(query).executeBatch(tup).mapEmpty())
        .onFailure(
            failureHandler -> {
              LOGGER.error("insertPolicy fail :: " + failureHandler.getLocalizedMessage());
              p.fail(INTERNALERROR);
            })
        .onSuccess(success -> p.complete(true));

    return p.future();
  }

  public Future<List<Tuple>> createTuple(
      List<CreatePolicyRequest> req, Map<String, ResourceObj> resourceObj, User user) {
    Promise<List<Tuple>> p = Promise.promise();
    try {
      List<Tuple> tuples = new ArrayList<>();
      for (CreatePolicyRequest obj : req) {

        String catId = obj.getItemId();
        String userId = obj.getUserId();
        UUID itemId = resourceObj.get(catId).getId();
        String itemType = obj.getItemType().toUpperCase();
        UUID providerId;
        if (itemType.equals(itemTypes.RESOURCE_SERVER.toString()))
          providerId = UUID.fromString(user.getUserId());
        else providerId = resourceObj.get(catId).getOwnerId();
        String status = "ACTIVE";
        String expTime = obj.getExpiryTime();
        LocalDateTime expiryTime;
        if (!obj.getExpiryTime().isEmpty())
          expiryTime = LocalDateTime.parse(expTime, DateTimeFormatter.ISO_DATE_TIME);
        else {
          if (itemType.equals(itemTypes.RESOURCE_SERVER.toString()))
            expiryTime =
                LocalDateTime.now(ZoneOffset.UTC)
                    .plusMonths(Integer.parseInt(options.getString("adminPolicyExpiry")));
          else
            expiryTime =
                LocalDateTime.now(ZoneOffset.UTC)
                    .plusMonths(Integer.parseInt(options.getString("policyExpiry")));
        }
        JsonObject constraints = obj.getConstraints();
        tuples.add(Tuple.of(userId, itemId, itemType, providerId, status, expiryTime, constraints));
      }
      p.complete(tuples);
    } catch (Exception e) {
      LOGGER.error("createTuple fail " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  public Future<List<Tuple>> checkExistingPolicy(List<Tuple> tuples) {
    Promise<List<Tuple>> p = Promise.promise();

    List<Tuple> selectTuples = new ArrayList<>();
    for (int i = 0; i < tuples.size(); i++) {
      UUID userId = tuples.get(i).getUUID(0);
      UUID itemId = tuples.get(i).getUUID(1);
      String itemType = tuples.get(i).getString(2);
      UUID providerId = tuples.get(i).getUUID(3);
      String status = tuples.get(i).getString(4);
      selectTuples.add(Tuple.of(userId, itemId, itemType, providerId, status));
    }
    pool.withTransaction(
        conn ->
            conn.preparedQuery(CHECK_EXISTING_POLICY)
                .executeBatch(selectTuples)
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "checkExistingPolicy fail :: " + failureHandler.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    ar -> {
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

                      if (ids.size() > 0) {
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_ALREADY_EXISTS)
                                .title(DUPLICATE_POLICY)
                                .detail(ids.get(0).toString())
                                .status(409)
                                .build();
                        p.fail(new ComposeException(r));
                      } else p.complete(tuples);
                    }));

    return p.future();
  }

  public Future<Map<String, ResourceObj>> getResSerDetails(List<String> servers, String userId) {

    Promise<Map<String, ResourceObj>> p = Promise.promise();

    if (servers.isEmpty()) {
      Response r =
          new Response.ResponseBuilder()
              .type(URN_INVALID_INPUT)
              .title(BAD_REQUEST)
              .detail(BAD_REQUEST)
              .status(400)
              .build();
      p.fail(new ComposeException(r));
      return p.future();
    }

    Collector<Row, ?, List<JsonObject>> resDetailCollector =
        Collectors.mapping(row -> row.toJson(), Collectors.toList());

    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_RES_SER)
                .collecting(resDetailCollector)
                .execute(
                    Tuple.of(UUID.fromString(userId))
                        .addArrayOfString(servers.toArray(String[]::new)))
                .map(res -> res.value())
                .onFailure(
                    obj -> {
                      LOGGER.error("checkResSer db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    success -> {
                      List<String> validServers =
                          success.stream()
                              .map(JsonObject.class::cast)
                              .filter(tagObject -> !tagObject.getString(CAT_ID).isEmpty())
                              .map(tagObject -> tagObject.getString(CAT_ID))
                              .collect(Collectors.toList());

                      servers.removeAll(validServers);
                      if (!servers.isEmpty()) {
                        Response r =
                            new Response.ResponseBuilder()
                                .status(400)
                                .type(URN_INVALID_INPUT)
                                .title(SERVER_NOT_PRESENT)
                                .detail(servers.toString())
                                .build();
                        p.fail(new ComposeException(r));
                      } else {
                        Map<String, ResourceObj> respMap = new HashMap<>();
                        for (JsonObject serverObject : success) {
                          serverObject.put(ITEMTYPE, RESOURCE_SERVER_TABLE);
                          respMap.put(
                              serverObject.getString(CAT_ID), new ResourceObj(serverObject));
                        }
                        p.complete(respMap);
                      }
                    }));
    return p.future();
  }

  /**
   * checks if there is a policy for user by auth admin
   *
   * @param userId - userId of user
   * @return Boolean - active policy exits true , else false
   */
  public Future<Boolean> checkAuthPolicy(String userId) {
    Promise<Boolean> p = Promise.promise();
    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_AUTH_POLICY)
                .execute(Tuple.of(userId, options.getString("authServerUrl"), status.ACTIVE))
                .onFailure(
                    obj -> {
                      LOGGER.error("checkAuthPolicy db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.rowCount() > 0) p.complete(true);
                      else {
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_INVALID_INPUT)
                                .title(NO_AUTH_POLICY)
                                .detail(NO_AUTH_POLICY)
                                .status(403)
                                .build();
                        p.fail(new ComposeException(r));
                      }
                    }));

    return p.future();
  }

  public Future<Boolean> checkAuthTrusteePolicy(String providerId, List<UUID> apdIds) {
    Promise<Boolean> p = Promise.promise();
    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_TRUSTEE_POLICY)
                .execute(Tuple.of(providerId, status.ACTIVE, apdIds.toArray(UUID[]::new)))
                .onFailure(
                    obj -> {
                      LOGGER.error(
                          "checkAuthTrusteePolicy db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.rowCount() > 0) p.complete(true);
                      else {
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_INVALID_INPUT)
                                .title(NO_AUTH_TRUSTEE_POLICY)
                                .detail(NO_AUTH_TRUSTEE_POLICY)
                                .status(403)
                                .build();
                        p.fail(new ComposeException(r));
                      }
                    }));

    return p.future();
  }

  public Future<List<Tuple>> userPolicyDuplicate(
      List<CreatePolicyRequest> req, Map<String, ResourceObj> resourceObj, User user) {
    Promise<List<Tuple>> p = Promise.promise();
    Future<List<Tuple>> tuples = createTuple(req, resourceObj, user);
    Future<List<Tuple>> checkDuplicate = tuples.compose(this::checkExistingPolicy);
    checkDuplicate.onSuccess(ar -> p.complete(ar)).onFailure(failure -> p.fail(failure));
    return p.future();
  }

  public Future<List<Tuple>> createApdTuple(
      List<CreatePolicyRequest> req, Map<String, ResourceObj> resourceObj, JsonObject apdDetails) {
    Promise<List<Tuple>> p = Promise.promise();
    try {
      List<Tuple> tuples = new ArrayList<>();
      for (CreatePolicyRequest obj : req) {
        String catId = obj.getItemId();
        String apdUrl = obj.getApdId();
        JsonObject apd = apdDetails.getJsonObject(apdUrl);
        UUID apdId = UUID.fromString(apd.getString(ID));
        String userClass = obj.getUserClass();
        UUID itemId = resourceObj.get(catId).getId();
        String itemType = obj.getItemType().toUpperCase();
        UUID providerId = resourceObj.get(catId).getOwnerId();
        String status = "ACTIVE";
        String expTime = obj.getExpiryTime();
        LocalDateTime expiryTime;
        if (!obj.getExpiryTime().isEmpty())
          expiryTime = LocalDateTime.parse(expTime, DateTimeFormatter.ISO_DATE_TIME);
        else {
          if (itemType.equals(itemTypes.RESOURCE_SERVER.toString()))
            expiryTime =
                LocalDateTime.now(ZoneOffset.UTC)
                    .plusMonths(Integer.parseInt(options.getString("adminPolicyExpiry")));
          else
            expiryTime =
                LocalDateTime.now(ZoneOffset.UTC)
                    .plusMonths(Integer.parseInt(options.getString("policyExpiry")));
        }
        JsonObject constraints = obj.getConstraints();
        tuples.add(
            Tuple.of(
                apdId, userClass, itemId, itemType, providerId, status, expiryTime, constraints));
      }
      p.complete(tuples);
    } catch (Exception e) {
      LOGGER.error("createApdTuple fail " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  public Future<List<Tuple>> checkExistingApdPolicy(List<Tuple> tuples) {
    Promise<List<Tuple>> p = Promise.promise();

    List<Tuple> selectTuples = new ArrayList<>();
    for (int i = 0; i < tuples.size(); i++) {
      UUID itemId = tuples.get(i).getUUID(2);
      String itemType = tuples.get(i).getString(3);
      UUID providerId = tuples.get(i).getUUID(4);
      String status = tuples.get(i).getString(5);
      selectTuples.add(Tuple.of(itemId, itemType, providerId, status));
    }
    pool.withTransaction(
        conn ->
            conn.preparedQuery(CHECK_EXISTING_APD_POLICY)
                .executeBatch(selectTuples)
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "checkExistingApdPolicy fail :: " + failureHandler.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    ar -> {
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

                      if (ids.size() > 0) {
                        Response r =
                            new Response.ResponseBuilder()
                                .type(URN_ALREADY_EXISTS)
                                .title(DUPLICATE_POLICY)
                                .detail(ids.get(0).toString())
                                .status(409)
                                .build();
                        p.fail(new ComposeException(r));
                      } else p.complete(tuples);
                    }));

    return p.future();
  }

  public Future<List<Tuple>> apdPolicyDuplicate(
      List<CreatePolicyRequest> req,
      Map<String, ResourceObj> resourceObj,
      JsonObject apdDetails) {
    Promise<List<Tuple>> p = Promise.promise();
    Future<List<Tuple>> tuples = createApdTuple(req, resourceObj, apdDetails);
    Future<List<Tuple>> checkDuplicate = tuples.compose(this::checkExistingApdPolicy);
    checkDuplicate.onSuccess(ar -> p.complete(ar)).onFailure(failure -> p.fail(failure));
    return p.future();
  }
}
