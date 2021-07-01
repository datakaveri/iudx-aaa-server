package iudx.aaa.server.policy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.UUID;
import static iudx.aaa.server.policy.Constants.*;

/**
 * The Policy Service Implementation.
 * <h1>Policy Service Implementation</h1>
 * <p>
 * The Policy Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.policy.PolicyService}.
 * </p>
 *
 * @version 1.0
 * @since 2020-12-15
 */

public class PolicyServiceImpl implements PolicyService {

    private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);

    private PgPool pool;

    // Create the pooled client

    public PolicyServiceImpl(PgPool pool)
    {
        this.pool = pool;
    }

    @Override
    public PolicyService createPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
        // TODO Auto-generated method stub
        LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
        JsonObject response = new JsonObject();
        response.put("status", "success");
        handler.handle(Future.succeededFuture(response));
        return this;
    }

    @Override
    public PolicyService deletePolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
        // TODO Auto-generated method stub
        LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

        JsonObject response = new JsonObject();
        response.put("status", "success");
        handler.handle(Future.succeededFuture(response));
        return this;
    }

    @Override
    public PolicyService listPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
        // TODO Auto-generated method stub
        LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
        JsonObject response = new JsonObject();
        response.put("status", "success");
        handler.handle(Future.succeededFuture(response));
        return this;
    }


    @Override
    public PolicyService verifyPolicy(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

        JsonObject response = new JsonObject();
        UUID userId = UUID.fromString(request.getString(USER_ID));
        String itemId = request.getString(ITEM_ID);
        String itemType = request.getString(ITEM_TYPE).toUpperCase();
        String role = request.getString(ROLE).toUpperCase();

        Future<String> getRoles =  pool.withConnection(conn ->
                conn.preparedQuery(GET_FROM_ROLES_TABLE)
                        .execute(Tuple.of(userId,roles.valueOf(role),status.APPROVED)).compose(ar -> {

                    RowSet<Row> rows = ar;

                    if(rows.rowCount() > 0) {
                        return Future.succeededFuture(rows.iterator().next().getString(ROLE));
                    }
                    return Future.failedFuture(ROLE_NOT_FOUND);
                }));

        Future<JsonObject> getConstraints = getRoles.compose(res -> pool.withConnection(conn ->
                conn.preparedQuery(GET_FROM_POLICY_TABLE +  itemType.toLowerCase() + GET_FROM_POLICY_TABLE_JOIN )
                        .execute(Tuple.of(userId,itemType,itemId))).compose(ar -> {
            RowSet<Row> rows = ar;

            if(rows.rowCount() > 0) {
                JsonObject resp = new JsonObject();
                resp = rows.iterator().next().toJson();
                resp.put(ROLE,res.toLowerCase());
                return Future.succeededFuture(resp);
            }

            return Future.failedFuture(POLICY_NOT_FOUND);
        }));

        Future<Void> checkDelegate ;
        if(role.equals(roles.DELEGATE.toString())) {
            checkDelegate = getConstraints.compose(ar ->
                    pool.withConnection(conn -> conn.preparedQuery(CHECK_DELEGATE)
                            .execute(Tuple.of(ar.getString(OWNER_ID), userId, status.ACTIVE)).compose(res -> {
                                if (res.rowCount() > 0)
                                    return Future.succeededFuture();
                                return Future.failedFuture(NOT_DELEGATE);
                            })));
        }
        else
            checkDelegate = getConstraints.compose(ar -> Future.succeededFuture());

        Future<JsonObject> getResource = checkDelegate.compose(ar ->
                pool.withConnection(conn ->
                        conn.preparedQuery(GET_URL +  itemType.toLowerCase() + GET_URL_JOIN )
                                .execute(Tuple.of(itemId))).compose(res -> {
                    if(res.rowCount() > 0) {
                        Row rows = res.iterator().next();
                        JsonObject details = new JsonObject();
                        details.mergeIn(getConstraints.result());
                        details.remove(OWNER_ID);
                        details.put(STATUS,SUCCESS);
                        details.put(CAT_ID,itemId);
                        details.put(URL, rows.getString(URL));
                        return Future.succeededFuture(details);
                    }
                    return  Future.failedFuture(URL_NOT_FOUND);
                }));

        getResource.onSuccess(
                s -> {
                    handler.handle(Future.succeededFuture(s));
                });

        getResource.onFailure(f -> {
            handler.handle(Future.failedFuture(f.getLocalizedMessage()));

        });

        return this;

    }
}
