package org.cdpg.dx.aaa.credit.service;


import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.dao.CreditDeductionDAO;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.dao.impl.CreditRequestDAOImpl;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.database.postgres.PostgresVerticle;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.cdpg.dx.common.Constants.PG_SERVICE_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CreditServiceTest {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
    .withDatabaseName("testdb")
    .withUsername("testuser")
    .withPassword("testpass");

  @BeforeAll
  void startContainer() {
    POSTGRES.start();
  }

  @AfterAll
  void stopContainer() {
    POSTGRES.stop();
  }

  @BeforeEach
  void deployPostgresVerticle(Vertx vertx, VertxTestContext testContext) {
    String createTableSql = """
                CREATE TABLE IF NOT EXISTS credit_requests (
                  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
                  user_id UUID NOT NULL,
                  amount	DECIMAL,
                  status VARCHAR NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
                  requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  processed_at TIMESTAMP WITHOUT TIME ZONE
                );
                """;

    try (var conn = POSTGRES.createConnection("")) {
      var stmt = conn.createStatement();
      stmt.execute(createTableSql);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create table", e);
    }

    JsonObject config = new JsonObject()
      .put("databaseIP", POSTGRES.getHost())
      .put("databasePort", POSTGRES.getMappedPort(5432))
      .put("databaseName", POSTGRES.getDatabaseName())
      .put("databaseUserName", POSTGRES.getUsername())
      .put("databasePassword", POSTGRES.getPassword())
      .put("poolSize", 5);

    vertx.deployVerticle(
      new PostgresVerticle(),
      new DeploymentOptions().setConfig(config),
      testContext.succeedingThenComplete()
    );
  }

  @Test
  void test_create_credit_request(Vertx vertx, VertxTestContext testContext) {
    PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

    CreditRequestDAO creditRequestDAO = new CreditRequestDAOImpl(postgresService);

    UserCreditDAO mockUserCreditDAO = Mockito.mock(UserCreditDAO.class);
    CreditDeductionDAO mockCreditDeductionDAO = Mockito.mock(CreditDeductionDAO.class);

    CreditDAOFactory factory = new CreditDAOFactory(postgresService) {
      @Override public CreditRequestDAO creditRequestDAO() { return creditRequestDAO; }
      @Override public UserCreditDAO userCreditDAO() { return mockUserCreditDAO; }
      @Override public CreditDeductionDAO creditDeductionDAO() { return mockCreditDeductionDAO; }
    };

    CreditService creditService = new CreditServiceImpl(factory);

    UUID userId = UUID.randomUUID();
    double amount = 750.0;


    creditService.createCreditRequest(userId,amount).onComplete(ar -> {
      if (ar.succeeded()) {
        CreditRequest result = ar.result();
        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertEquals(amount, result.amount(), 0.001);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });

//    Status status = Status.PENDING;
//    creditService.getAllRequestsByStatus(status).onComplete(ar -> {
//      if (ar.succeeded()) {
//        List<CreditRequest> result = ar.result();
////        assertNotNull(result);
////        assertEquals(userId, result.userId());
////        assertEquals(amount, result.amount(), 0.001);
//        testContext.completeNow();
//      } else {
//        testContext.failNow(ar.cause());
//      }
//    });


  }
}
