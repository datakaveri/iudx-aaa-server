package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrganizationDAOImpl implements OrganizationDAO {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationDAOImpl.class);
    private final PostgresService postgresService;

    public OrganizationDAOImpl(PostgresService postgresService) {
        this.postgresService = postgresService;
    }

    @Override
    public Future<Organization> create(Organization organization) {
        // Extract columns and values from the organization DTO
        var organizationMap = organization.toNonEmptyFieldsMap();
        List<String> columns = List.copyOf(organizationMap.keySet());
        List<Object> values = List.copyOf(organizationMap.values());

        InsertQuery query = new InsertQuery();
        query.setTable(Constants.ORGANIZATION_TABLE);
        query.setColumns(columns);
        query.setValues(values);

        return postgresService.insert(query)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Insert query returned no rows.");
                    }
                    return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error inserting organization: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<Organization> update(UUID id, UpdateOrgDTO updateOrgDTO) {
        var updateFields = updateOrgDTO.toNonEmptyFieldsMap();
        List<String> columns = List.copyOf(updateFields.keySet());
        List<Object> values = List.copyOf(updateFields.values());

      Condition condition = new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(id.toString()));
      UpdateQuery query = new UpdateQuery(Constants.ORGANIZATION_TABLE, columns, values, condition, null, null);

      return postgresService.update(query)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Update query returned no rows.");
                    }
                    return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error updating organization with ID {}: {}", id, err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<Organization> get(UUID orgId) {
        SelectQuery query = new SelectQuery(
                Constants.ORGANIZATION_TABLE,
                Constants.ALL_ORG_FIELDS,
                new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(orgId.toString())),
                null, null, null, null
        );

        return postgresService.select(query)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Select query returned no rows for organization ID " + orgId);
                    }
                    return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error fetching organization with ID {}: {}", orgId, err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<Boolean> delete(UUID id) {
      Condition condition = new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(id.toString()));
        DeleteQuery query = new DeleteQuery(Constants.ORGANIZATION_TABLE, condition, null, null);

        return postgresService.delete(query)
                .compose(result -> {
                    if (!result.isRowsAffected()) {
                        return Future.failedFuture("No rows updated when deleting organization with ID " + id);
                    }
                    return Future.succeededFuture(true);
                })
                .recover(err -> {
                    LOGGER.error("Error deleting organization with ID {}: {}", id, err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }

    @Override
    public Future<List<Organization>> getAll() {
        SelectQuery query = new SelectQuery(
                Constants.ORGANIZATION_TABLE,
                List.of("*"),
                null, null, null, null, null
        );

        return postgresService.select(query)
                .compose(result -> {
                    List<Organization> organizations = result.getRows().stream()
                            .map(row -> Organization.fromJson((JsonObject) row))
                            .collect(Collectors.toList());
                    return Future.succeededFuture(organizations);
                })
                .recover(err -> {
                    LOGGER.error("Error fetching all organizations: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
    }
}
