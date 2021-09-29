package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.*;

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
   * @param req - set of userId
   * @return Set<UUID> -> set of uuids present in the db
   */
  public Future<Void> validateExpiry(List<String> req) {
    Promise<Void> p = Promise.promise();
    try {

      for (String obj : req) {
        LocalDateTime expTime = LocalDateTime.parse(obj, DateTimeFormatter.ISO_DATE_TIME);
        if (expTime.compareTo(LocalDateTime.now(ZoneOffset.UTC)) < 0) {
          p.fail(VALIDATE_EXPIRY_FAIL + expTime.toString());
          break;
        }
      }

    } catch (DateTimeParseException e) {
      p.fail(INVALID_DATETIME + e.getParsedString());
    } catch (Exception e) {
      p.fail(INTERNALERROR + e.toString());
    }
    if (!p.future().failed()) p.complete();
    return p.future();
  }

  /**
   * Check if the users exist in users table
   *
   * @param users - set of userId
   * @return Set<UUID> -> set of uuids present in the db
   */
  public Future<Set<UUID>> checkUserExist(Set<UUID> users) {

    Promise<Set<UUID>> p = Promise.promise();

    if (users.isEmpty()) {
      p.fail(BAD_REQUEST);
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
                        p.fail(INVALID_USER + users.toString());
                      } else p.complete(obj.value());
                    }));
    return p.future();
  }

  /**
   * Insert into policy table
   *
   * @param req - List of requests
   * @param resourceId - map of cat_id to item_id
   * @param ownerId - map of cat_id to owner_id
   * @return Boolean - true if insertion is true
   */
  public Future<Boolean> insertPolicy(
      List<CreatePolicyRequest> req,
      Map<String, UUID> resourceId,
      Map<String, UUID> ownerId,
      User user) {
    Promise<Boolean> p = Promise.promise();
    Future<List<Tuple>> tuples = createTuple(req, resourceId, ownerId, user);

    Future<List<Tuple>> checkDuplicate = tuples.compose(this::checkExistingPolicy);


    checkDuplicate
        .compose(
            success ->
                pool.withTransaction(
                    conn -> conn.preparedQuery(INSERT_POLICY).executeBatch(success).mapEmpty()))
        .onFailure(
            failureHandler -> {
              LOGGER.error("checkDuplicate fail :: " + failureHandler.getLocalizedMessage());
              p.fail(failureHandler.getLocalizedMessage());
            })
        .onSuccess(success -> p.complete(true));

    return p.future();
  }

  public Future<List<Tuple>> createTuple(
      List<CreatePolicyRequest> req,
      Map<String, UUID> resourceId,
      Map<String, UUID> ownerId,
      User user) {
    Promise<List<Tuple>> p = Promise.promise();
    try {
      List<Tuple> tuples = new ArrayList<>();
      for (CreatePolicyRequest obj : req) {

        String catId = obj.getItemId();
        String userId = obj.getUserId();
        UUID itemId = resourceId.get(catId);
        String itemType = obj.getItemType().toUpperCase();
        UUID providerId;
        if (itemType.equals(itemTypes.RESOURCE_SERVER.toString()))
          providerId = UUID.fromString(user.getUserId());
        else providerId = ownerId.get(catId);
        String status = "ACTIVE";
        String expTime = obj.getExpiryTime();
        LocalDateTime expiryTime;
        if (!obj.getExpiryTime().isEmpty())
          expiryTime = LocalDateTime.parse(expTime, DateTimeFormatter.ISO_DATE_TIME);
        else {
          if (itemType.equals(itemTypes.RESOURCE_SERVER.toString()))
            expiryTime = LocalDateTime.now(ZoneOffset.UTC)
                .plusMonths(Integer.parseInt(options.getString("adminPolicyExpiry")));
          else
            expiryTime = LocalDateTime.now(ZoneOffset.UTC)
                .plusMonths(Integer.parseInt(options.getString("policyExpiry")));
        }
        JsonObject constraints = obj.getConstraints();
        tuples.add(Tuple.of(userId, itemId, itemType, providerId, status, expiryTime, constraints));
      }
      p.complete(tuples);
    } catch (Exception e) {
      p.fail(e.toString());
    }
    return p.future();
  }

  public Future<List<Tuple>> checkExistingPolicy(List<Tuple> tuples) {
    Promise<List<Tuple>> p = Promise.promise();
    Collector<Row, ?, List<UUID>> policyIdCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());
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
                .collecting(policyIdCollector)
                .executeBatch(selectTuples)
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "checkExistingPolicy fail :: " + failureHandler.getLocalizedMessage());
                      p.fail(failureHandler.getLocalizedMessage());
                    })
                .onSuccess(
                    ar -> {
                      if (ar.size() > 0) {
                        p.fail(DUPLICATE_POLICY + ar.value().get(0));
                      } else p.complete(tuples);
                    }));

    return p.future();
  }

  public Response getRespObj(String obj) {
    Response.ResponseBuilder r = new Response.ResponseBuilder();

    String errorMessage;
    if (obj.contains(":")) errorMessage = obj.split(":")[0] + ":";
    else errorMessage = obj;
    switch (errorMessage) {
      case BAD_REQUEST:
        {
          r.type(URN_INVALID_INPUT);
          r.title(BAD_REQUEST);
          r.detail(BAD_REQUEST);
          r.status(400);
          break;
        }
      case VALIDATE_EXPIRY_FAIL:
        {
          r.type(URN_INVALID_INPUT);
          r.title(VALIDATE_EXPIRY_FAIL.split(":")[0]);
          r.detail(obj.replace(VALIDATE_EXPIRY_FAIL, ""));
          r.status(400);
          break;
        }
      case INVALID_DATETIME:
        {
          r.type(URN_INVALID_INPUT);
          r.title(INVALID_DATETIME.split(":")[0]);
          r.detail(obj.replace(INVALID_DATETIME, ""));
          r.status(400);
          break;
        }

      case INVALID_USER:
        {
          r.type(URN_INVALID_INPUT);
          r.title(INVALID_USER.split(":")[0]);
          r.detail(obj.replace(INVALID_USER, ""));
          r.status(400);
          break;
        }
      case SERVER_NOT_PRESENT:
        {
          r.type(URN_INVALID_INPUT);
          r.title(SERVER_NOT_PRESENT.split(":")[0]);
          r.detail(obj.replace(SERVER_NOT_PRESENT, ""));
          r.status(400);
          break;
        }
      case NO_AUTH_POLICY:
        {
          r.type(URN_INVALID_ROLE);
          r.title(NO_AUTH_POLICY);
          r.detail(NO_AUTH_POLICY);
          r.status(403);
          break;
        }
      case UNAUTHORIZED:
        {
          r.type(URN_INVALID_INPUT);
          r.title(UNAUTHORIZED);
          r.detail(UNAUTHORIZED);
          r.status(403);
          break;
        }
      case ITEMNOTFOUND:
        {
          r.type(URN_INVALID_INPUT);
          r.title(ITEMNOTFOUND);
          r.detail(obj.replace(ITEMNOTFOUND, ""));
          r.status(400);
          break;
        }
      case DUPLICATE_POLICY:
        {
          r.type(URN_ALREADY_EXISTS);
          r.title(DUPLICATE_POLICY.split(":")[0]);
          r.detail(obj.replace(DUPLICATE_POLICY, ""));
          r.status(409);
          break;
        }
      case PROVIDER_NOT_REGISTERED:
      {
        r.type(URN_INVALID_INPUT);
        r.title(PROVIDER_NOT_REGISTERED);
        r.detail(PROVIDER_NOT_REGISTERED);
        r.status(403);
        break;
      }

      default:
        {
          r.type(URN_INVALID_INPUT);
          r.title(INTERNALERROR);
          r.detail(INTERNALERROR);
          r.status(500);
          break;
        }
    }
    Response resp = r.build();
    return resp;
  }
}
