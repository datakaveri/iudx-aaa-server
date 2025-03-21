package iudx.aaa.server.auditing;

import static iudx.aaa.server.auditing.util.Constants.BODY;
import static iudx.aaa.server.auditing.util.Constants.BODY_COLUMN_INDEX;
import static iudx.aaa.server.auditing.util.Constants.DATABASE_TABLE_NAME;
import static iudx.aaa.server.auditing.util.Constants.EMPTY_RESPONSE;
import static iudx.aaa.server.auditing.util.Constants.ENDPOINT;
import static iudx.aaa.server.auditing.util.Constants.ENDPOINT_COLUMN_INDEX;
import static iudx.aaa.server.auditing.util.Constants.ERROR;
import static iudx.aaa.server.auditing.util.Constants.FAILED;
import static iudx.aaa.server.auditing.util.Constants.MESSAGE;
import static iudx.aaa.server.auditing.util.Constants.METHOD;
import static iudx.aaa.server.auditing.util.Constants.METHOD_COLUMN_INDEX;
import static iudx.aaa.server.auditing.util.Constants.QUERY_KEY;
import static iudx.aaa.server.auditing.util.Constants.RESULTS;
import static iudx.aaa.server.auditing.util.Constants.SUCCESS;
import static iudx.aaa.server.auditing.util.Constants.TIME;
import static iudx.aaa.server.auditing.util.Constants.TIME_COLUMN_INDEX;
import static iudx.aaa.server.auditing.util.Constants.USERID_COLUMN_INDEX;
import static iudx.aaa.server.auditing.util.Constants.USERID_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.USER_ID;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.aaa.server.auditing.util.QueryBuilder;
import iudx.aaa.server.auditing.util.ResponseBuilder;
import java.sql.Timestamp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuditingServiceImpl implements AuditingService {

  private static final Logger LOGGER = LogManager.getLogger(AuditingServiceImpl.class);
  PgConnectOptions connectOptions;
  PoolOptions poolOptions;
  PgPool pool;
  private Vertx vertx;
  private QueryBuilder queryBuilder = new QueryBuilder();
  private JsonObject query = new JsonObject();
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private String databaseTableName;
  private int databasePoolSize;
  private ResponseBuilder responseBuilder;

  public AuditingServiceImpl(JsonObject propObj, Vertx vertxInstance) {

    if (propObj != null && !propObj.isEmpty()) {
      databaseIP = propObj.getString("auditingDatabaseIP");
      databasePort = propObj.getInteger("auditingDatabasePort");
      databaseName = propObj.getString("auditingDatabaseName");
      databaseUserName = propObj.getString("auditingDatabaseUserName");
      databasePassword = propObj.getString("auditingDatabasePassword");
      databaseTableName = propObj.getString("auditingDatabaseTableName");
      databasePoolSize = propObj.getInteger("auditingPoolSize");
    }

    this.connectOptions =
        new PgConnectOptions()
            .setPort(databasePort)
            .setHost(databaseIP)
            .setDatabase(databaseName)
            .setUser(databaseUserName)
            .setPassword(databasePassword)
            .setReconnectAttempts(2)
            .setReconnectInterval(1000);

    this.vertx = vertxInstance;
    this.poolOptions = new PoolOptions().setMaxSize(databasePoolSize);
    this.pool = PgPool.pool(vertxInstance, connectOptions, poolOptions);
  }

  @Override
  public Future<JsonObject> executeWriteQuery(JsonObject request) {
    Promise<JsonObject> promiseHandler = Promise.promise();

    request.put(DATABASE_TABLE_NAME, databaseTableName);
    query = queryBuilder.buildWritingQuery(request);

    if (query.containsKey(ERROR)) {
      LOGGER.error("Fail: Query returned with an error: " + query.getString(ERROR));
      responseBuilder =
          new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(query.getString(ERROR));
      promiseHandler.fail(responseBuilder.getResponse().toString());
      return promiseHandler.future();
    }

    Future<JsonObject> result = writeInDatabase(query);
    result.onComplete(
        resultHandler -> {
          if (resultHandler.succeeded()) {
            promiseHandler.complete(resultHandler.result());
          } else if (resultHandler.failed()) {
            LOGGER.error("failed ::" + resultHandler.cause());
            promiseHandler.fail(resultHandler.cause().getMessage());
          }
        });
    return promiseHandler.future();
  }

  @Override
  public Future<JsonObject> executeReadQuery(JsonObject request) {
    LOGGER.debug("Info: Read Query" + request.toString());
    Promise<JsonObject> promiseHandler = Promise.promise();

    if (!request.containsKey(USER_ID)) {
      LOGGER.debug("Info: " + USERID_NOT_FOUND);
      responseBuilder =
          new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(USERID_NOT_FOUND);
      promiseHandler.fail(responseBuilder.getResponse().toString());
      return promiseHandler.future();
    }
    request.put(DATABASE_TABLE_NAME, databaseTableName);
    query = queryBuilder.buildReadingQuery(request);

    if (query.containsKey(ERROR)) {
      LOGGER.error("Fail: Query returned with an error: " + query.getString(ERROR));
      responseBuilder =
          new ResponseBuilder(FAILED).setTypeAndTitle(400).setMessage(query.getString(ERROR));
      promiseHandler.fail(responseBuilder.getResponse().toString());
      return promiseHandler.future();
    }
    LOGGER.debug("Info: Query constructed: " + query.getString(QUERY_KEY));

    Future<JsonObject> result = runReadQuery(query);
    result.onComplete(
        resultHandler -> {
          if (resultHandler.succeeded()) {
            LOGGER.info("Read from DB succeeded.");
            promiseHandler.complete(resultHandler.result());
          } else if (resultHandler.failed()) {
            LOGGER.error("Read from DB failed:" + resultHandler.cause());
            promiseHandler.fail(resultHandler.cause().getMessage());
          }
        });
    return promiseHandler.future();
  }

  private Future<JsonObject> runReadQuery(JsonObject query) {
    Promise<JsonObject> promise = Promise.promise();
    JsonArray jsonArray = new JsonArray();
    pool.withConnection(connection -> connection.query(query.getString(QUERY_KEY)).execute())
        .onComplete(
            rows -> {
              RowSet<Row> result = rows.result();
              if (result == null) {
                responseBuilder =
                    new ResponseBuilder(FAILED).setTypeAndTitle(204).setMessage(EMPTY_RESPONSE);
                promise.complete(responseBuilder.getResponse());
              }
              if (result != null) {
                for (Row rs : result) {
                  jsonArray.add(getJsonObject(rs));
                }
                if (jsonArray.isEmpty()) {
                  responseBuilder =
                      new ResponseBuilder(FAILED).setTypeAndTitle(204).setMessage(EMPTY_RESPONSE);
                } else {
                  responseBuilder =
                      new ResponseBuilder(SUCCESS).setTypeAndTitle(200).setJsonArray(jsonArray);
                  LOGGER.debug("Info: RESPONSE" + responseBuilder.getResponse().getString(RESULTS));
                }
                promise.complete(responseBuilder.getResponse());
              }
            });
    return promise.future();
  }

  private Future<JsonObject> writeInDatabase(JsonObject query) {
    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();
    pool.withConnection(connection -> connection.query(query.getString(QUERY_KEY)).execute())
        .onComplete(
            rows -> {
              if (rows.succeeded()) {
                response.put(MESSAGE, "Table Updated Successfully");
                responseBuilder =
                    new ResponseBuilder(SUCCESS)
                        .setTypeAndTitle(200)
                        .setMessage(response.getString(MESSAGE));
                LOGGER.debug("Info: " + responseBuilder.getResponse().toString());
                promise.complete(responseBuilder.getResponse());
              }
              if (rows.failed()) {
                LOGGER.error("Info: failed :" + rows.cause());
                response.put(MESSAGE, rows.cause().getMessage());
                responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(400)
                        .setMessage(response.getString(MESSAGE));
                LOGGER.debug("Info: " + responseBuilder.getResponse().toString());
                promise.fail(responseBuilder.getResponse().toString());
              }
            });
    return promise.future();
  }

  private JsonObject getJsonObject(Row rs) {
    JsonObject entries = new JsonObject();
    LOGGER.debug("COUNT: " + (rs.getString(METHOD_COLUMN_INDEX)));
    LOGGER.debug("TIME: " + (rs.getLong(TIME_COLUMN_INDEX)));
    LOGGER.debug("USERID: " + (rs.getString(USERID_COLUMN_INDEX)));
    LOGGER.debug("BODY: " + (rs.getString(BODY_COLUMN_INDEX).replaceAll("\"", " ")));
    LOGGER.debug("ENDPOINT: " + (rs.getString(ENDPOINT_COLUMN_INDEX)));

    entries.put(METHOD, rs.getString(METHOD_COLUMN_INDEX));
    entries.put(TIME, new Timestamp(rs.getLong(TIME_COLUMN_INDEX)).toLocalDateTime().toString());
    entries.put(USER_ID, rs.getString(USERID_COLUMN_INDEX));
    entries.put(BODY, rs.getString(BODY_COLUMN_INDEX).replaceAll("\"", " "));
    entries.put(ENDPOINT, rs.getString(ENDPOINT_COLUMN_INDEX));
    return entries;
  }
}
