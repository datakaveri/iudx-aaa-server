package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.BAD_REQUEST;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY_DELEGATION;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_ROLES;
import static iudx.aaa.server.policy.Constants.DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.DUPLICATE_POLICY;
import static iudx.aaa.server.policy.Constants.GET_SERVER_DETAILS;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INSERT_DELEGATION;
import static iudx.aaa.server.policy.Constants.INSERT_POLICY;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.INVALID_DATETIME;
import static iudx.aaa.server.policy.Constants.INVALID_USER;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.NOT_DELEGATE;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.PROVIDER_NOT_REGISTERED;
import static iudx.aaa.server.policy.Constants.SERVER_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.URN_ALREADY_EXISTS;
import static iudx.aaa.server.policy.Constants.URN_INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.USER_ID;
import static iudx.aaa.server.policy.Constants.roles;
import static iudx.aaa.server.policy.Constants.status;
import static iudx.aaa.server.policy.Constants.VALIDATE_EXPIRY_FAIL;

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
                        p.fail(NOT_DELEGATE + resp.get(0));
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
                        p.fail(SERVER_NOT_PRESENT + result.get(0));
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
                      if (obj.rowCount() <= 0) p.fail(NO_AUTH_POLICY);
                      else p.complete(true);
                    }));
    return p.future();
  }
    public Future<Boolean> insertItems(List<Tuple> tuples) {
    Promise<Boolean> p = Promise.promise();
      Future<List<Tuple>>  checkDuplicate = checkExistingDelegation(tuples);

      checkDuplicate.compose(success ->
              pool.withTransaction(
                      conn -> conn.preparedQuery(INSERT_DELEGATION).executeBatch(success).mapEmpty()))
              .onFailure(
                      failureHandler -> {
                          LOGGER.error("insertItems fail :: " + failureHandler.getLocalizedMessage());
                          p.fail(failureHandler.getLocalizedMessage());
                      })
              .onSuccess(success -> p.complete(true));

        return p.future();

    }

    public Future<List<Tuple>> checkExistingDelegation(List<Tuple> tuples) {
        Promise<List<Tuple>> p = Promise.promise();
        Collector<Row, ?, List<UUID>> policyIdCollector =
                Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());

        pool.withTransaction(
                conn ->
                        conn.preparedQuery(CHECK_EXISTING_DELEGATIONS)
                                .collecting(policyIdCollector)
                                .executeBatch(tuples)
                                .onFailure(
                                        failureHandler -> {
                                            LOGGER.error(
                                                    "checkExistingDelegation fail :: " + failureHandler.getLocalizedMessage());
                                            p.fail(failureHandler.getLocalizedMessage());
                                        })
                                .onSuccess(
                                        ar -> {
                                            if (ar.size() > 0) {
                                                p.fail(DUPLICATE_DELEGATION + ar.value().get(0));
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

            case NOT_DELEGATE:
            {
                r.type(URN_INVALID_ROLE);
                r.title(NOT_DELEGATE);
                r.detail(obj.replace(NOT_DELEGATE, ""));
                r.status(400);
                break;
            }
            case SERVER_NOT_PRESENT:
            {
                r.type(URN_INVALID_INPUT);
                r.title(SERVER_NOT_PRESENT);
                r.detail(obj.replace(SERVER_NOT_PRESENT, ""));
                r.status(400);
                break;
            }
            case NO_AUTH_POLICY:
            {
                r.type(URN_INVALID_INPUT);
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
            case DUPLICATE_DELEGATION:
            {
                r.type(URN_ALREADY_EXISTS);
                r.title(DUPLICATE_DELEGATION);
                r.detail(obj.replace(DUPLICATE_DELEGATION, ""));
                r.status(409);
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
