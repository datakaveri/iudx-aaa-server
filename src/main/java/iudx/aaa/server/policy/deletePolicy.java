package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
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

import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_INFO;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECK_DELPOLICY;
import static iudx.aaa.server.policy.Constants.CHECK_POLICY_EXIST;
import static iudx.aaa.server.policy.Constants.DELEGATE_CHECK;
import static iudx.aaa.server.policy.Constants.DELETE_POLICY;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ID_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.OWNERID;
import static iudx.aaa.server.policy.Constants.OWNER_ID;
import static iudx.aaa.server.policy.Constants.PROVIDER_ID;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_ID;
import static iudx.aaa.server.policy.Constants.RES_OWNER_CHECK;
import static iudx.aaa.server.policy.Constants.itemTypes;
import static iudx.aaa.server.policy.Constants.status;

public class deletePolicy {
  private static final Logger LOGGER = LogManager.getLogger(deletePolicy.class);

  private final PgPool pool;
  private final JsonObject options;

  public deletePolicy(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }

  public Future<Map<UUID,JsonObject>> checkPolicyExist(List<UUID> req) {
    Promise<Map<UUID,JsonObject>> p = Promise.promise();

      Collector<Row, ?, Map<UUID, JsonObject>> idCollector =
              Collectors.toMap(
                      row -> row.getUUID(ID),
                      row ->
                              new JsonObject()
                                      .put(ID, row.getUUID(ID))
                                      .put(ITEMTYPE, row.getString(ITEM_TYPE))
                                      .put(OWNERID, row.getUUID(OWNER_ID)));

    try {

      pool.withConnection(
          conn ->
              conn.preparedQuery(CHECK_POLICY_EXIST)
                  .collecting(idCollector)
                  .execute(
                      Tuple.of(Constants.status.ACTIVE).addArrayOfUUID(req.toArray(UUID[]::new)))
                      .map(map -> map.value())
                  .onFailure(
                      obj -> {
                        LOGGER.error("CHECK_POLICY_EXIST db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      obj -> {
                        List<UUID> resp = new ArrayList<>();
                        resp.addAll(req);
                        resp.removeAll(obj.keySet());
                        if(resp.isEmpty())
                            p.complete(obj);
                        else{
                            Response r =
                                    new Response.ResponseBuilder()
                                            .type(URN_MISSING_INFO)
                                            .title(ID_NOT_PRESENT)
                                            .detail(ID_NOT_PRESENT + resp.toString())
                                            .status(400)
                                            .build();
                            p.fail(new ComposeException(r));
                        }

                      }));
      return p.future();
    } catch (Exception e) {
      LOGGER.error("Fail: ");
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  public Future<List<UUID>> CheckResOwner(List<UUID> req, String userId) {
    Promise<List<UUID>> p = Promise.promise();
    Collector<Row, ?, List<UUID>> idCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());
    try {
      pool.withConnection(
          conn ->
              conn.preparedQuery(RES_OWNER_CHECK)
                  .collecting(idCollector)
                  .execute(Tuple.of(userId, status.ACTIVE).addArrayOfUUID(req.toArray(UUID[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("CheckResOwner db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      obj -> {
                        List<UUID> resp = new ArrayList<>();
                        resp.addAll(req);
                        resp.removeAll(obj.value());
                        p.complete(resp);
                      }));

    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  public Future<List<UUID>> checkDelegate(String userId, List<UUID> request) {
    Promise<List<UUID>> p = Promise.promise();
    Collector<Row, ?, List<UUID>> idCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());
    try {

      pool.withConnection(
          conn ->
              conn.preparedQuery(DELEGATE_CHECK)
                  .collecting(idCollector)
                  .execute(
                      Tuple.of(userId, Constants.status.ACTIVE, options.getString("authServerUrl"))
                          .addArrayOfUUID(request.toArray(UUID[]::new)))
                  .onSuccess(
                      obj -> {
                        List<UUID> resp = new ArrayList<>();
                        resp.addAll(request);
                        resp.removeAll(obj.value());
                        p.complete(resp);
                      })
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkDelegate db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      }));
    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  public Future<Boolean> checkDelegatePolicy(String userId, List<UUID> req) {
    Promise<Boolean> p = Promise.promise();
    try {
      pool.withConnection(
              conn ->
                  conn.preparedQuery(CHECK_DELPOLICY)
                      .execute(
                          Tuple.of(
                              userId,
                              itemTypes.RESOURCE_SERVER,
                              options.getString("authServerUrl"),
                              status.ACTIVE))
                      .compose(
                          obj -> {
                            if (obj.rowCount() > 0) {
                              p.complete(true);
                            } else {
                              LOGGER.error(
                                  "checkDelegatePolicy :: Auth delegate policy does not exist");
                              p.complete(false);
                            }
                            return p.future();
                          }))
          .onFailure(
              fail -> {
                LOGGER.error("checkDelegatePolicy Fail: " + fail.getLocalizedMessage());
                p.fail(INTERNALERROR);
              });

    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail(INTERNALERROR);
    }

    return p.future();
  }

  public Future<Boolean> delPolicy(List<UUID> request) {
    Promise<Boolean> p = Promise.promise();
    try {
      pool.withConnection(
              conn ->
                  conn.preparedQuery(DELETE_POLICY)
                      .execute(
                          Tuple.of(Constants.status.DELETED, Constants.status.ACTIVE)
                              .addArrayOfUUID(request.toArray(UUID[]::new)))
                      .onSuccess(rows -> p.complete(Boolean.TRUE)))
          .onFailure(
              obj -> {
                LOGGER.error("delPolicy db fail :: " + obj.getLocalizedMessage());
                p.fail(INTERNALERROR);
              });
    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }
}
