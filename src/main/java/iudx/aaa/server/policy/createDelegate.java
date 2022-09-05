package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY_DELEGATION;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.CHECK_ROLES;
import static iudx.aaa.server.policy.Constants.DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.GET_SERVER_DETAILS;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INSERT_DELEGATION;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.NOT_DELEGATE;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.SERVER_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.USER_ID;
import static iudx.aaa.server.policy.Constants.roles;
import static iudx.aaa.server.policy.Constants.status;

public class createDelegate {
  private static final Logger LOGGER = LogManager.getLogger(createDelegate.class);
  private final PgPool pool;
  private final JsonObject options;

  public createDelegate(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }
  /**
   * Validate user roles
   *
   * @param users - list of userId
   * @return <Void> -> success if all present, fail if not
   */
  public Future<Void> checkUserRoles(List<UUID> users) {
    Promise<Void> p = Promise.promise();

    Collector<Row, ?, List<UUID>> userIdCollector =
        Collectors.mapping(row -> row.getUUID(USER_ID), Collectors.toList());

    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_ROLES)
                .collecting(userIdCollector)
                .execute(
                    Tuple.of(
                        roles.DELEGATE.toString(),
                        status.APPROVED.toString(),
                        users.toArray(UUID[]::new)))
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "failed checkUserRole db: " + failureHandler.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.value().containsAll(users)) p.complete();
                      else {
                        List<UUID> resp = new ArrayList<>();
                        resp.addAll(users);
                        resp.removeAll(obj.value());
                        p.fail(new ComposeException
                            (400,URN_INVALID_ROLE,NOT_DELEGATE,resp.get(0).toString()));
                      }
                    }));
    return p.future();
  }

  /**
   * Validate user roles time
   *
   * @param resSer - list of resource server url
   * @return <Map<String,UUID>> -> Map with key as url and value as resource_server_id
   */
  public Future<Map<String, UUID>> getResourceServerDetails(List<String> resSer) {
    Promise<Map<String, UUID>> p = Promise.promise();

    Collector<Row, ?, Map<String, UUID>> idCollector =
        Collectors.toMap(row -> row.getString(URL), row -> row.getUUID(ID));
    pool.withConnection(
        conn ->
            conn.preparedQuery(GET_SERVER_DETAILS)
                .collecting(idCollector)
                .execute(Tuple.of(resSer.toArray(String[]::new)))
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "failed getResourceServerDetails db: "
                              + failureHandler.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      Map<String, UUID> servers = obj.value();
                      if (servers.keySet().containsAll(resSer)) p.complete(servers);
                      else {
                        List<String> result = resSer;
                        result.removeAll(servers.keySet());
                        p.fail(new ComposeException
                            (400,URN_INVALID_INPUT,SERVER_NOT_PRESENT,result.get(0)));
                      }
                    }));
    return p.future();
  }

  public Future<Boolean> checkAuthPolicy(String userId) {
    Promise<Boolean> p = Promise.promise();
    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_AUTH_POLICY_DELEGATION)
                .execute(
                    Tuple.of(
                        userId,
                        Constants.itemTypes.RESOURCE_SERVER,
                        options.getString("authServerUrl"),
                        Constants.status.ACTIVE))
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "failed getResourceServerDetails db: "
                              + failureHandler.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.rowCount() <= 0)  p.fail(new ComposeException
                          (403,URN_INVALID_INPUT,NO_AUTH_POLICY,NO_AUTH_POLICY));
                      else p.complete(true);
                    }));
    return p.future();
  }

  public Future<Boolean> insertItems(List<Tuple> tuples) {
      Promise<Boolean> p = Promise.promise();
      Future<List<Tuple>> checkDuplicate = checkExistingDelegation(tuples);

      checkDuplicate
          .compose(
              success ->
                  pool.withTransaction(
                      conn -> conn.preparedQuery(INSERT_DELEGATION).executeBatch(success).mapEmpty()))
          .onFailure(
              failureHandler -> {
                  LOGGER.error("insertItems fail :: " + failureHandler.getLocalizedMessage());
                  p.fail(failureHandler);
              })
          .onSuccess(success -> p.complete(true));

      return p.future();

  }

  public Future<List<Tuple>> checkExistingDelegation(List<Tuple> tuples) {
    Promise<List<Tuple>> p = Promise.promise();

    pool.withTransaction(
        conn ->
            conn.preparedQuery(CHECK_EXISTING_DELEGATIONS)
                .executeBatch(tuples)
                .onFailure(
                    failureHandler -> {
                      LOGGER.error(
                          "checkExistingDelegation fail :: "
                              + failureHandler.getLocalizedMessage());
                      p.fail(failureHandler.getLocalizedMessage());
                    })
                .onSuccess(
                    ar -> {
                        //This check to get response when batch query is executed for select
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
                          p.fail(new ComposeException
                            (409,URN_ALREADY_EXISTS,DUPLICATE_DELEGATION,ids.toString()));
                      } else p.complete(tuples);
                    }));

    return p.future();
  }
}
