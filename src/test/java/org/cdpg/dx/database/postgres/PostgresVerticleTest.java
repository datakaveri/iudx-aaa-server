package org.cdpg.dx.database.postgres;

import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    void setup(Vertx vertx, VertxTestContext testContext) {
        // Step 1: Create table using JDBC
        String jdbcUrl = POSTGRES.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            String createTableSql = """
                CREATE TABLE IF NOT EXISTS organization_create_requests (
                    id UUID PRIMARY KEY,
                    description TEXT,
                    document_path TEXT,
                    name TEXT,
                    status TEXT
                );
                """;

            stmt.execute(createTableSql);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create table", e);
        }

        // Step 2: Deploy PostgresVerticle with the config
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
        PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

//        InsertQuery query = new InsertQuery();
//        query.setTable("organization_create_requests");
//        query.setColumns(List.of("description", "document_path", "name", "status"));
//        query.setValues(List.of("Test description", "/docs/test.pdf", "Test Org", "ACTIVE"));
//
//        postgresService.insert(query).onComplete(ar -> {
//            if (ar.succeeded()) {
//                Assertions.assertTrue(ar.result().isRowsAffected(), "Insert should affect at least 1 row");
//                testContext.completeNow();
//            } else {
//                testContext.failNow(ar.cause());
//            }
//        });

        SelectQuery selectQuery = new SelectQuery();

        selectQuery.setTable("organization_create_requests");
        selectQuery.setColumns(List.of("*"));

        ConditionComponent condition = new Condition("id", Condition.Operator.EQUALS , List.of(UUID.randomUUID().toString()));
        selectQuery.setCondition(condition);
        postgresService.select(selectQuery).onComplete(ar -> {
            if (ar.succeeded()) {
                Assertions.assertTrue(ar.result().isRowsAffected(), "Insert should affect at least 1 row");
                testContext.completeNow();
            } else {
                testContext.failNow(ar.cause());
            }
        });


//        DeleteQuery deleteQuery = new DeleteQuery();
//
//        deleteQuery.setTable("organization_create_requests");
//
//        Condition delConditon = new Condition("id", Condition.Operator.EQUALS , List.of(UUID.randomUUID().toString()));
//        deleteQuery.setCondition(delConditon);
//        deleteQuery.setLimit(null);
//        deleteQuery.setOrderBy(new ArrayList<>());
//        System.out.println(deleteQuery.toSQL());
//        postgresService.delete(deleteQuery).onComplete(ar -> {
//            if (ar.succeeded()) {
//                Assertions.assertTrue(ar.result().isRowsAffected(), "Insert should affect at least 1 row");
//                testContext.completeNow();
//            } else {
//                testContext.failNow(ar.cause());
//            }
//        });
    }
}
