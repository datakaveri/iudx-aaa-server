package iudx.aaa.server.auditing;

import static iudx.aaa.server.auditing.util.Constants.API;
import static iudx.aaa.server.auditing.util.Constants.BODY;
import static iudx.aaa.server.auditing.util.Constants.DATA_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.DETAIL;
import static iudx.aaa.server.auditing.util.Constants.END_TIME;
import static iudx.aaa.server.auditing.util.Constants.INVALID_DATE_TIME;
import static iudx.aaa.server.auditing.util.Constants.INVALID_TIME;
import static iudx.aaa.server.auditing.util.Constants.METHOD;
import static iudx.aaa.server.auditing.util.Constants.MISSING_END_TIME;
import static iudx.aaa.server.auditing.util.Constants.MISSING_START_TIME;
import static iudx.aaa.server.auditing.util.Constants.START_TIME;
import static iudx.aaa.server.auditing.util.Constants.USERID_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.aaa.server.configuration.Configuration;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Unit tests for auditing service. */
@ExtendWith({VertxExtension.class})
public class AuditingServiceTest {
  private static AuditingService auditingService;
  private static Vertx vertxObj;
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int databasePoolSize;
  private static String databaseTableName;
  private static Configuration config;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertex(Vertx vertx, VertxTestContext vertxTestContext) {
    vertxObj = vertx;
    config = new Configuration();
    JsonObject dbConfig = config.configLoader(5, vertx);
    databaseIP = dbConfig.getString("auditingDatabaseIP");
    databasePort = dbConfig.getInteger("auditingDatabasePort");
    databaseName = dbConfig.getString("auditingDatabaseName");
    databaseUserName = dbConfig.getString("auditingDatabaseUserName");
    databasePassword = dbConfig.getString("auditingDatabasePassword");
    databasePoolSize = dbConfig.getInteger("auditingPoolSize");
    databaseTableName = dbConfig.getString("auditingDatabaseTableName");
    auditingService = new AuditingServiceImpl(dbConfig, vertxObj);
    vertxTestContext.completeNow();
  }

  private JsonObject writeRequest() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put(USER_ID, "/userId");
    jsonObject.put(METHOD, "POST");
    JsonObject s = new JsonObject();
    s.put("type", "urn:dx:as:Success");
    s.put("title", "policy read");
    s.put("result", "policy read");
    jsonObject.put(BODY, s);
    jsonObject.put(API, "/post");
    return jsonObject;
  }

  @Test
  @DisplayName("Failure-Testing write query for missing endpoint")
  void writeForMissingEndpoint(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = writeRequest();
    jsonObject.remove(API);

    auditingService
        .executeWriteQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              DATA_NOT_FOUND,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing write query for missing body")
  void writeForMissingBody(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = writeRequest();
    jsonObject.remove(BODY);

    auditingService
        .executeWriteQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              DATA_NOT_FOUND,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing write query for missing userId")
  void writeForMissingUserId(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = writeRequest();
    jsonObject.remove(USER_ID);

    auditingService
        .executeWriteQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              DATA_NOT_FOUND,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing write query for missing method")
  void writeForMissingMethod(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = writeRequest();
    jsonObject.remove(METHOD);

    auditingService
        .executeWriteQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              DATA_NOT_FOUND,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Success-Testing Write Query")
  void writeData(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = writeRequest();

    auditingService
        .executeWriteQuery(jsonObject)
        .onComplete(
            vertxTestContext.succeeding(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertTrue(response.getString("title").equals("Success"));
                          vertxTestContext.completeNow();
                        })));
  }

  private JsonObject readRequest() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("userId", "/userId");
    jsonObject.put("method", "POST");
    jsonObject.put("endPoint", "/post");
    jsonObject.put("startTime", ZonedDateTime.now().minusSeconds(5).toString());
    jsonObject.put("endTime", ZonedDateTime.now().toString());
    return jsonObject;
  }

  @Test
  @DisplayName("Success- Reading Query")
  void readData(VertxTestContext vertxTestContext) {
    /*
     * We write to DB first instead of using ordered tests (without order, writeData can occur at
     * any point of time)
     */
    JsonObject dataToWrite = writeRequest();

    auditingService
        .executeWriteQuery(dataToWrite)
        .onComplete(
            written -> {
              // create readRequest here so that query endTime is
              // only after the write is done
              JsonObject jsonObject = readRequest();
              auditingService
                  .executeReadQuery(jsonObject)
                  .onComplete(
                      vertxTestContext.succeeding(
                          response ->
                              vertxTestContext.verify(
                                  () -> {
                                    assertTrue(response.getString("title").equals("Success"));
                                    vertxTestContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Failure-Testing read query for missing userId")
  void ReadForMissingUserId(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readRequest();
    jsonObject.remove(USER_ID);

    auditingService
        .executeReadQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              USERID_NOT_FOUND,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing read query for missing startTime")
  void ReadForMissingStartTime(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readRequest();
    jsonObject.remove(START_TIME);

    auditingService
        .executeReadQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              MISSING_START_TIME,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing read query for missing endTime")
  void ReadForMissingEndTime(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readRequest();
    jsonObject.remove(END_TIME);

    auditingService
        .executeReadQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              MISSING_END_TIME,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing read query when endTime is before startTime")
  void ReadForEndTimeBeforeStartTime(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readRequest();
    String temp = jsonObject.getString(START_TIME);
    jsonObject.put(START_TIME, jsonObject.getString(END_TIME));
    jsonObject.put(END_TIME, temp);

    auditingService
        .executeReadQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              INVALID_TIME,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Failure-Testing read query for invalid date time format")
  void ReadForInvalidDateTimeFormat(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readRequest();
    String temp = "1970-01-0105:30:00+05:30[Asia/Kolkata]";
    jsonObject.put(START_TIME, temp);
    auditingService
        .executeReadQuery(jsonObject)
        .onComplete(
            vertxTestContext.failing(
                response ->
                    vertxTestContext.verify(
                        () -> {
                          assertEquals(
                              INVALID_DATE_TIME,
                              new JsonObject(response.getMessage()).getString(DETAIL));
                          vertxTestContext.completeNow();
                        })));
  }
}
