package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrganizationCreateRequestDAOImpl implements OrganizationCreateRequestDAO {

    private final PostgresService postgresService;
    private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestDAOImpl.class);

    public OrganizationCreateRequestDAOImpl(PostgresService postgresService) {
        this.postgresService = postgresService;
    }

    @Override
    public Future<OrganizationCreateRequest> create(OrganizationCreateRequest request) {
        // Get columns and values directly from the DTO map
        InsertQuery insertQuery = new InsertQuery();
        insertQuery.setTable(Constants.ORG_CREATE_REQUEST_TABLE);
        insertQuery.setColumns(List.copyOf(request.toNonEmptyFieldsMap().keySet()));
        insertQuery.setValues(List.copyOf(request.toNonEmptyFieldsMap().values()));

        LOGGER.debug("Insert Query: {}", insertQuery.toSQL());
        LOGGER.debug("Query Params: {}", insertQuery.getQueryParams());

        return postgresService.insert(insertQuery)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Insert query returned no rows.");
                    }
                    return Future.succeededFuture(OrganizationCreateRequest.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error inserting organization create request: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<OrganizationCreateRequest> getById(UUID id) {
        SelectQuery selectQuery = new SelectQuery(
                Constants.ORG_CREATE_REQUEST_TABLE,
                Constants.ALL_ORG_CREATE_REQUEST_FIELDS,
                new Condition(Constants.ORG_CREATE_ID, Condition.Operator.EQUALS, List.of(id)),
                null, null, null, null
        );

        return postgresService.select(selectQuery)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("No request found with the given ID.");
                    }
                    return Future.succeededFuture(OrganizationCreateRequest.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error fetching request by ID: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<Boolean> updateStatus(UUID requestId, Status status) {
        UpdateQuery updateQuery = new UpdateQuery(
                Constants.ORG_CREATE_REQUEST_TABLE,
                List.of(Constants.STATUS, Constants.UPDATED_AT),
                List.of(status.toString(), Instant.now().toString()),
                new Condition(Constants.ORG_CREATE_ID, Condition.Operator.EQUALS, List.of(requestId)),
                null,
                null
        );

        return postgresService.update(updateQuery)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Update query returned no rows.");
                    }
                    return Future.succeededFuture(true);
                })
                .recover(err -> {
                    LOGGER.error("Error updating request status: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<List<OrganizationCreateRequest>> getAllByStatus(Status status) {
        SelectQuery query = new SelectQuery(
                Constants.ORG_CREATE_REQUEST_TABLE,
                List.of("*"),
                new Condition(Constants.STATUS, Condition.Operator.EQUALS, List.of(status.toString())),
                null, null, null, null
        );

        return postgresService.select(query)
                .compose(result -> {
                    List<OrganizationCreateRequest> requests = result.getRows().stream()
                            .map(row -> OrganizationCreateRequest.fromJson((JsonObject) row))
                            .collect(Collectors.toList());
                    return Future.succeededFuture(requests);
                })
                .recover(err -> {
                    LOGGER.error("Error fetching requests by status: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }
}
