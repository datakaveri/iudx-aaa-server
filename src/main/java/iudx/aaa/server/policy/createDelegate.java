package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_DELEGATIONS;
import static iudx.aaa.server.policy.Constants.DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INSERT_DELEGATION;

public class createDelegate {
  private static final Logger LOGGER = LogManager.getLogger(createDelegate.class);
  private final PgPool pool;
  private final JsonObject options;

  public createDelegate(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }

  /**
   * Insert delegations.
   * @param tuples
   * @return
   */
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

  /**
   * Check if delegations existing.
   * @param tuples
   * @return
   */
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
