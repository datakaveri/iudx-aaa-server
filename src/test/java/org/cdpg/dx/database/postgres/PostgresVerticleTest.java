package org.cdpg.dx.database.postgres;

import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.cdpg.dx.common.Constants.PG_SERVICE_ADDRESS;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgresVerticleTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    void setupContainer() {
        POSTGRES.start();
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
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
                deployment -> {
                    if (deployment.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(deployment.cause());
                    }
                }
        );
    }

    @Test
    void test_insert_query(Vertx vertx, VertxTestContext testContext) {
        // ✅ Create test table using JDBC
        String jdbcUrl = POSTGRES.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            String createTableSql = """
                CREATE TABLE IF NOT EXISTS organization_create_requests (
                    id SERIAL PRIMARY KEY,
                    description TEXT,
                    document_path TEXT,
                    name TEXT,
                    status TEXT
                );
                """;

            stmt.execute(createTableSql);

        } catch (Exception e) {
            testContext.failNow(e);
            return;
        }

        vertx.setTimer(500, id -> {
            PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

            InsertQuery query = new InsertQuery();
            query.setTable("organization_create_requests");
            query.setColumns(List.of("description", "document_path", "name", "status"));
            query.setValues(List.of("Test description", "/docs/test.pdf", "Test Org", "ACTIVE"));

            postgresService.insert(query).onComplete(ar -> {
                if (ar.succeeded()) {
                    Assertions.assertTrue(ar.result().isRowsAffected(), "Insert should affect at least 1 row");
                    testContext.completeNow();
                } else {
                    testContext.failNow(ar.cause());
                }
            });
        });
    }
}
