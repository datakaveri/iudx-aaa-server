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
        String jdbcUrl = POSTGRES.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS organizations (
                    id UUID PRIMARY KEY,
                    name TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS organization_create_requests (
                    id UUID PRIMARY KEY,
                    organization_id UUID,
                    description TEXT,
                    document_path TEXT,
                    name TEXT,
                    status TEXT
                );
            """);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create tables", e);
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
    void test_select_with_join(Vertx vertx, VertxTestContext testContext) {
        PostgresService postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);

        UUID orgId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        // Insert org
        InsertQuery insertOrg = new InsertQuery();
        insertOrg.setTable("organizations");
        insertOrg.setColumns(List.of("id", "name"));
        insertOrg.setValues(List.of(orgId.toString(), "Test Organization"));

        // Insert request
        InsertQuery insertRequest = new InsertQuery();
        insertRequest.setTable("organization_create_requests");
        insertRequest.setColumns(List.of("id", "organization_id", "description", "document_path", "name", "status"));
        insertRequest.setValues(List.of(requestId.toString(), orgId.toString(), "Join test", "/path/doc.pdf", "Req Name", "ACTIVE"));

        // Chain both inserts before running the join select
        postgresService.insert(insertOrg).compose(orgRes -> {
            Assertions.assertTrue(orgRes.isRowsAffected());
            return postgresService.insert(insertRequest);
        }).compose(reqRes -> {
            Assertions.assertTrue(reqRes.isRowsAffected());

            // Now perform the JOIN
            SelectQuery query = new SelectQuery();
            query.setTable("organization_create_requests");
            query.setTableAlias("ocr");
            query.setColumns(List.of("ocr.id", "ocr.name", "orgs.name AS organization_name"));

            Join join = new Join();
            join.setJoinType(Join.JoinType.INNER);
            join.setTable("organizations");
            join.setTableAlias("orgs");
            join.setJoinColumn("id");        // from 'organizations'
            join.setOnColumn("ocr.organization_id"); // from 'ocr'

            query.setJoins(List.of(join));
            query.setCondition(new Condition("ocr.id", Condition.Operator.EQUALS, List.of(requestId.toString())));

            return postgresService.select(query);
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                System.out.println("Join Select Result: " + ar.result().getRows());
                Assertions.assertFalse(ar.result().getRows().isEmpty(), "Expected result from join query");
                testContext.completeNow();
            } else {
                testContext.failNow(ar.cause());
            }
        });
    }
}
