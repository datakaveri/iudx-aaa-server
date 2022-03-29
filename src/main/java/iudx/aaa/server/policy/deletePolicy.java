package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.APD_POL;
import static iudx.aaa.server.policy.Constants.DELETE_APD_POLICY;
import static iudx.aaa.server.policy.Constants.DELETE_USR_POLICY;
import static iudx.aaa.server.policy.Constants.EXISTING_ACTIVE_APD_POL;
import static iudx.aaa.server.policy.Constants.EXISTING_ACTIVE_USR_POL;
import static iudx.aaa.server.policy.Constants.EXISTING_ACTIVE_USR_POL_NO_RES_SER;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ID_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.USR_POL;

public class deletePolicy {
  private static final Logger LOGGER = LogManager.getLogger(deletePolicy.class);

  private final PgPool pool;
  private final JsonObject options;

  public deletePolicy(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }

  /**
   * Check if a list of policy IDs exist in either the user policies or APD policies table and are
   * active.
   * 
   * @param ids list of policy IDs in UUID form
   * @param user the User object of the user calling the API
   * @param delegateInfo JSON object containing the provider's user ID, in case the API was called
   *        by an auth delegate
   * @return a future of a Map of String to a list of policy IDs in UUID format, where the String
   *         refers to the kind of policy Ids (USER/APD). In case policy IDs are not found, a failed
   *         future with a ComposeException is returned.
   */
  public Future<Map<String, List<UUID>>> checkPolicyExist(List<UUID> ids, User user,
      JsonObject delegateInfo) {

    Promise<Map<String, List<UUID>>> p = Promise.promise();
    String policyTableQuery;
    UUID ownerId;

    if (delegateInfo.isEmpty()) {
      policyTableQuery = EXISTING_ACTIVE_USR_POL;
      ownerId = UUID.fromString(user.getUserId());
    } else {
      /*
       * In case the auth delegate is delegated to a user having both PROVIDER and ADMIN roles, the
       * following query disallows the auth delegate from deleting admin policies (i.e. where item
       * type is RESOURCE_SERVER)
       */
      policyTableQuery = EXISTING_ACTIVE_USR_POL_NO_RES_SER;
      ownerId = UUID.fromString(delegateInfo.getString("providerId"));
    }

    Tuple commonTuple = Tuple.of(ownerId).addArrayOfUUID(ids.toArray(UUID[]::new));

    Collector<Row, ?, List<UUID>> idCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());

    Future<List<UUID>> userPolicyQuery =
        pool.withConnection(conn -> conn.preparedQuery(policyTableQuery).collecting(idCollector)
            .execute(commonTuple).map(map -> map.value()));

    Future<List<UUID>> apdPolicyQuery =
        pool.withConnection(conn -> conn.preparedQuery(EXISTING_ACTIVE_APD_POL)
            .collecting(idCollector).execute(commonTuple).map(map -> map.value()));

    CompositeFuture.all(userPolicyQuery, apdPolicyQuery).onSuccess(success -> {
      List<UUID> userPolIds = userPolicyQuery.result();
      List<UUID> apdPolIds = apdPolicyQuery.result();

      if (userPolIds.size() + apdPolIds.size() < ids.size()) {
        ids.removeAll(userPolIds);
        ids.removeAll(apdPolIds);
        Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(ID_NOT_PRESENT)
            .detail(ID_NOT_PRESENT + ids.toString()).status(400).build();
        p.fail(new ComposeException(r));
        return;
      }

      /*
       * Sum of sizes of list should never be greater that the original list, since there's very
       * little chance UUIDs would be same across tables.
       */
      Map<String, List<UUID>> result = new HashMap<String, List<UUID>>();
      result.put(USR_POL, userPolIds);
      result.put(APD_POL, apdPolIds);
      p.complete(result);
    }).onFailure(e -> {
      LOGGER.error("Fail: " + e.getMessage());
      p.fail(INTERNALERROR);
    });

    return p.future();
  }

  /**
   * Deletes user and APD policies.
   * 
   * @param policyIds a Map of String to a list of policy IDs in UUID format, where the String
   *        refers to the kind of policy Ids (USER/APD)
   * @return A boolean future
   */
  public Future<Boolean> delPolicy(Map<String, List<UUID>> policyIds) {
    Promise<Boolean> p = Promise.promise();

    Tuple userPolTuple = Tuple.of(policyIds.get(USR_POL).toArray(UUID[]::new));
    Tuple apdPolTuple = Tuple.of(policyIds.get(APD_POL).toArray(UUID[]::new));

    pool.withTransaction(conn -> conn.preparedQuery(DELETE_USR_POLICY).execute(userPolTuple)
        .compose(success -> conn.preparedQuery(DELETE_APD_POLICY).execute(apdPolTuple)))
        .onSuccess(rows -> p.complete(Boolean.TRUE)).onFailure(obj -> {
          LOGGER.error("delPolicy db fail :: " + obj.getLocalizedMessage());
          p.fail(INTERNALERROR);
        });
    return p.future();
  }
}
