package iudx.aaa.server.auditing.util;

import static iudx.aaa.server.auditing.util.Constants.API;
import static iudx.aaa.server.auditing.util.Constants.BODY;
import static iudx.aaa.server.auditing.util.Constants.DATABASE_TABLE_NAME;
import static iudx.aaa.server.auditing.util.Constants.DATA_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.ENDPOINT;
import static iudx.aaa.server.auditing.util.Constants.ENDPOINT_QUERY;
import static iudx.aaa.server.auditing.util.Constants.END_TIME;
import static iudx.aaa.server.auditing.util.Constants.END_TIME_QUERY;
import static iudx.aaa.server.auditing.util.Constants.ERROR;
import static iudx.aaa.server.auditing.util.Constants.INVALID_DATE_TIME;
import static iudx.aaa.server.auditing.util.Constants.INVALID_TIME;
import static iudx.aaa.server.auditing.util.Constants.METHOD;
import static iudx.aaa.server.auditing.util.Constants.METHOD_QUERY;
import static iudx.aaa.server.auditing.util.Constants.MISSING_END_TIME;
import static iudx.aaa.server.auditing.util.Constants.MISSING_START_TIME;
import static iudx.aaa.server.auditing.util.Constants.QUERY_KEY;
import static iudx.aaa.server.auditing.util.Constants.READ_QUERY;
import static iudx.aaa.server.auditing.util.Constants.START_TIME;
import static iudx.aaa.server.auditing.util.Constants.START_TIME_QUERY;
import static iudx.aaa.server.auditing.util.Constants.USERID_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.USER_ID;
import static iudx.aaa.server.auditing.util.Constants.WRITE_QUERY;

import io.vertx.core.json.JsonObject;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QueryBuilder {

  private static final Logger LOGGER = LogManager.getLogger(QueryBuilder.class);

  //  primaryKey,body,endpoint,method,time,userid
  public JsonObject buildWritingQuery(JsonObject request) {

    if (!request.containsKey(BODY)
        || !request.containsKey(API)
        || !request.containsKey(METHOD)
        || !request.containsKey(USER_ID)) {
      return new JsonObject().put(ERROR, DATA_NOT_FOUND);
    }

    String primaryKey = UUID.randomUUID().toString().replace("-", "");
    String body = request.getJsonObject(BODY).toString();
    String endPoint = request.getString(API);
    String methodName = request.getString(METHOD);
    ZonedDateTime zst = ZonedDateTime.now();
    long time = getEpochTime(zst);
    String userId = request.getString(USER_ID);
    String databaseTableName= request.getString(DATABASE_TABLE_NAME);

    StringBuilder query =
        new StringBuilder(
            WRITE_QUERY
                .replace("$0",databaseTableName)
                .replace("$1", primaryKey)
                .replace("$2", body)
                .replace("$3", endPoint)
                .replace("$4", methodName)
                .replace("$5", Long.toString(time))
                .replace("$6", userId));

    LOGGER.info("Info: Query " + query);
    return new JsonObject().put(QUERY_KEY, query);
  }

  private long getEpochTime(ZonedDateTime time) {
    return time.toInstant().toEpochMilli();
  }

  public JsonObject buildReadingQuery(JsonObject request) {
    LOGGER.debug("Trying to build reading query.");

    String userId = request.getString(USER_ID);
    String startTime = request.getString(START_TIME);
    String endTime = request.getString(END_TIME);
    String method = request.getString(METHOD);
    String endPoint = request.getString(ENDPOINT);
    String databaseTableName= request.getString(DATABASE_TABLE_NAME);

    long fromTime = 0;
    long toTime = 0;
    ZonedDateTime zdt;

    if (!request.containsKey(USER_ID)) {
      return new JsonObject().put(ERROR, USERID_NOT_FOUND);
    }

    if (request.containsKey(START_TIME)) {
      /* check if the time is valid based on ISO 8601 format. */
      try {
        zdt = ZonedDateTime.parse(startTime);
        LOGGER.debug("Parsed date-time: " + zdt.toString());
      } catch (DateTimeParseException e) {
        LOGGER.error("Invalid Date-Time exception: " + e.getMessage());
        return new JsonObject().put(ERROR, INVALID_DATE_TIME);
      }

      if (!request.containsKey(END_TIME)) {
        return new JsonObject().put(ERROR, MISSING_END_TIME);
      }
    }

    if (request.containsKey(END_TIME)) {

      try {
        zdt = ZonedDateTime.parse(endTime);
        LOGGER.debug("Parsed date-time: " + zdt.toString());
      } catch (DateTimeParseException e) {
        LOGGER.error("Invalid Date-Time exception: " + e.getMessage());
        return new JsonObject().put(ERROR, INVALID_DATE_TIME);
      }
      if (!request.containsKey(START_TIME)) {
        return new JsonObject().put(ERROR, MISSING_START_TIME);
      }

      ZonedDateTime startZDT = ZonedDateTime.parse(startTime);
      ZonedDateTime endZDT = ZonedDateTime.parse(endTime);

      if (startZDT.isAfter(endZDT)) {
        LOGGER.error("Invalid Date-Time exception");
        return new JsonObject().put(ERROR, INVALID_TIME);
      }
      fromTime = getEpochTime(startZDT);
      toTime = getEpochTime(endZDT);
    }

    LOGGER.debug("Epoch fromTime: " + fromTime);
    LOGGER.debug("Epoch toTime: " + toTime);
    StringBuilder userIdQuery = new StringBuilder(READ_QUERY.replace("$0",databaseTableName).replace("$1", userId));
    LOGGER.debug("Info: QUERY " + userIdQuery);

    if (request.containsKey(START_TIME) && request.containsKey(END_TIME)) {
      StringBuilder tempQuery = userIdQuery;
      for (String s :
          Arrays.asList(
              START_TIME_QUERY.replace("$2", Long.toString(fromTime)),
              END_TIME_QUERY.replace("$3", Long.toString(toTime)))) tempQuery.append(s);
      userIdQuery = tempQuery;
      LOGGER.debug("Info: QUERY with start and end time" + userIdQuery);
    }
    if (request.containsKey(METHOD) && request.containsKey(ENDPOINT)) {
      StringBuilder tempQuery = userIdQuery;
      tempQuery.append(ENDPOINT_QUERY.replace("$4", endPoint));
      tempQuery.append(METHOD_QUERY.replace("$5", method));
      LOGGER.debug("Info: QUERY with method and endpoint " + tempQuery);
      return new JsonObject().put(QUERY_KEY, tempQuery);
    }
    return new JsonObject().put(QUERY_KEY, userIdQuery);
  }
}
