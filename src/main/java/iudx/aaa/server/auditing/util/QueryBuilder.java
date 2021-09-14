package iudx.aaa.server.auditing.util;

import static iudx.aaa.server.auditing.util.Constants.API;
import static iudx.aaa.server.auditing.util.Constants.BODY;
import static iudx.aaa.server.auditing.util.Constants.DATA_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.ERROR;
import static iudx.aaa.server.auditing.util.Constants.METHOD;
import static iudx.aaa.server.auditing.util.Constants.QUERY_KEY;
import static iudx.aaa.server.auditing.util.Constants.USER_ID;
import static iudx.aaa.server.auditing.util.Constants.WRITE_QUERY;

import io.vertx.core.json.JsonObject;
import java.time.ZonedDateTime;
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

    LOGGER.info("body: " + body);
    LOGGER.info("endpoint: " + endPoint);
    LOGGER.info("methodName: " + methodName);
    LOGGER.info("userId: " + userId);

    StringBuilder query =
        new StringBuilder(
            WRITE_QUERY
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
}
