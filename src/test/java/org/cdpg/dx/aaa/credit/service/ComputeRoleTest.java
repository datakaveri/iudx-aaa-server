package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.credit.dao.*;
import org.cdpg.dx.aaa.credit.dao.impl.ComputeRoleDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.CreditRequestDAOImpl;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.database.postgres.PostgresVerticle;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.aaa.credit.models.Status;


import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.cdpg.dx.common.util.ProxyAdressConstants.PG_SERVICE_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ComputeRoleTest {

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
      CREATE EXTENSION IF NOT EXISTS "pgcrypto";
      CREATE TABLE IF NOT EXISTS compute_role (
        id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
        user_id UUID NOT NULL,
        status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'approved')),
        approved_by UUID,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  void test_create_and_getAll_and_updateStatus(Vertx vertx, VertxTestContext testContext) {
    PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

    ComputeRoleDAO computeRoleDAO = new ComputeRoleDAOImpl(postgresService);

    CreditDAOFactory factory = new CreditDAOFactory(postgresService) {
      @Override public ComputeRoleDAO computeRoleDAO() { return computeRoleDAO; }
    };


    CreditService service = new CreditServiceImpl(factory);

    UUID userId = UUID.randomUUID();

    ComputeRole request = new ComputeRole(
      Optional.empty(),
      userId,
      Status.PENDING.getStatus(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );

    service.create(request).onComplete(testContext.succeeding(created -> {
      assertNotNull(created.id());
      assertEquals(userId, created.userId());
      assertEquals(Status.PENDING.getStatus(), created.status());

      service.getAll().onComplete(testContext.succeeding(all -> {
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(r -> r.userId().equals(userId)));

        UUID approvedBy = UUID.randomUUID();
        service.updateStatus(created.id().get(), Status.APPROVED, approvedBy)
          .onComplete(testContext.succeeding(updated -> {
            assertTrue(updated);
            testContext.completeNow();
          }));
      }));
    }));
  }
}

