package iudx.aaa.server.auditing;

import static iudx.aaa.server.auditing.util.Constants.API;
import static iudx.aaa.server.auditing.util.Constants.BODY;
import static iudx.aaa.server.auditing.util.Constants.DATA_NOT_FOUND;
import static iudx.aaa.server.auditing.util.Constants.DETAIL;
import static iudx.aaa.server.auditing.util.Constants.METHOD;
import static iudx.aaa.server.auditing.util.Constants.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.aaa.server.configuration.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
    auditingService = new AuditingServiceImpl(dbConfig, vertxObj);
    vertxTestContext.completeNow();
  }

  private JsonObject request() {
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
  @DisplayName("Testing write query for missing endpoint")
  void writeForMissingEndpoint(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = request();
    jsonObject.remove(API);

    auditingService.executeWriteQuery(
        jsonObject,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          DATA_NOT_FOUND, new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing write query for missing body")
  void writeForMissingBody(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = request();
    jsonObject.remove(BODY);

    auditingService.executeWriteQuery(
        jsonObject,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          DATA_NOT_FOUND, new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing write query for missing userId")
  void writeForMissingUserId(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = request();
    jsonObject.remove(USER_ID);

    auditingService.executeWriteQuery(
        jsonObject,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          DATA_NOT_FOUND, new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing write query for missing method")
  void writeForMissingMethod(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = request();
    jsonObject.remove(METHOD);

    auditingService.executeWriteQuery(
        jsonObject,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          DATA_NOT_FOUND, new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Write Query")
  void writeData(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = request();

    auditingService.executeWriteQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertTrue(response.getString("title").equals("Success"));
                      vertxTestContext.completeNow();
                    })));
  }
}
