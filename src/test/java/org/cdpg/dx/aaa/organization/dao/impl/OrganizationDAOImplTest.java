package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.database.postgres.PostgresVerticle;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

import io.vertx.core.DeploymentOptions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.cdpg.dx.common.Constants.PG_SERVICE_ADDRESS;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OrganizationDAOImplTest {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
    .withDatabaseName("testdb")
    .withUsername("testuser")
    .withPassword("testpass");

  private OrganizationDAO organizationDAO;

  @BeforeAll
  void startContainer() {
    POSTGRES.start();
  }

  @AfterAll
  void stopContainer() {
    POSTGRES.stop();
  }

  @BeforeEach
  void setup(Vertx vertx, VertxTestContext testContext) {
    // Create org table (simplified)
    String createTableSQL = """
                CREATE EXTENSION IF NOT EXISTS "pgcrypto";

                CREATE TABLE IF NOT EXISTS organization (
                id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
                name VARCHAR NOT NULL UNIQUE,
                description TEXT,
                document_path TEXT,
                created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            """;

    try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
      POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement stmt = conn.createStatement()) {
      stmt.execute(createTableSQL);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set up schema", e);
    }

    // Deploy PostgresVerticle
    JsonObject config = new JsonObject()
      .put("databaseIP", POSTGRES.getHost())
      .put("databasePort", POSTGRES.getMappedPort(5432))
      .put("databaseName", POSTGRES.getDatabaseName())
      .put("databaseUserName", POSTGRES.getUsername())
      .put("databasePassword", POSTGRES.getPassword())
      .put("poolSize", 5);

    vertx.deployVerticle(new PostgresVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);
        organizationDAO = new OrganizationDAOImpl(postgresService);
        testContext.completeNow();
      })
      .onFailure(testContext::failNow);
  }

  @Test
  void testCreateAndGetOrganization(VertxTestContext testContext) {
    UUID id = UUID.randomUUID();
    String orgName = "Test Org";
    String documentPath = "/path/to/doc";
    String description = "Testing description";

    Organization organization = new Organization(
      Optional.empty(),
      Optional.of(description),
      orgName,
      documentPath,
      Optional.empty(),
      Optional.empty()
    );

//        organizationDAO.delete(id).onComplete(deleted -> {
//            assertNotNull(deleted);
//            assertEquals(true, deleted.result());
//            testContext.completeNow();
//        });

    organizationDAO.create(organization).compose(created -> {
      assertNotNull(created);
      assertEquals(organization.orgName(), created.orgName());
      System.out.println("got id back >>>>>:" +created.id().toString());
      UUID newId = created.id().get();
      return organizationDAO.get(newId);
    }).onComplete(testContext.succeeding(retrieved -> {
      assertEquals(organization.id(), retrieved.id());
      assertEquals("Test Org", retrieved.orgName());
      testContext.completeNow();
    }));
  }
}
