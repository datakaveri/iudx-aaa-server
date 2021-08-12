package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.*;

public class deletePolicy {
  private static final Logger LOGGER = LogManager.getLogger(deletePolicy.class);

  private PgPool pool;

  public deletePolicy(PgPool pool) {
    this.pool = pool;
  }

  public Future<List<UUID>> checkResExist(List<UUID> req) {
    Promise<List<UUID>> p = Promise.promise();
    Collector<Row, ?, List<UUID>> idCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());
    try {

      pool.withConnection(
          conn ->
              conn.preparedQuery(CHECK_RES_EXIST)
                  .collecting(idCollector)
                  .execute(
                      Tuple.of(Constants.status.ACTIVE).addArrayOfUUID(req.toArray(UUID[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkResExist db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      obj -> {
                        List<UUID> resp = new ArrayList<>();
                        resp.addAll(req);
                        resp.removeAll(obj.value());
                        p.complete(resp);
                      }));
      return p.future();
    } catch (Exception e) {
      LOGGER.error("Fail: ");
      e.printStackTrace();
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
              conn.preparedQuery(
                      DELEGATE_CHECK)
                  .collecting(idCollector)
                  .execute(
                      Tuple.of(userId, Constants.status.ACTIVE)
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
                              userId, itemTypes.RESOURCE_SERVER, AUTH_SERVER_URL, status.ACTIVE))
                      .compose(
                          obj -> {
                            if (obj.rowCount() > 0) {
                              p.complete(true);
                            } else {
                              LOGGER.error("checkDelegatePolicy :: Auth delegate policy does not exist");
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
                  conn.preparedQuery(
                          DELETE_POLICY)
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
