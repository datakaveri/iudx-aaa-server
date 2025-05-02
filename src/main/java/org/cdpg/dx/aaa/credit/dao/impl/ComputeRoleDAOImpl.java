package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.ComputeRoleDAO;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ComputeRoleDAOImpl implements ComputeRoleDAO {

  private final PostgresService postgresService;

  private static final Logger LOGGER = LogManager.getLogger(ComputeRoleDAOImpl.class);


  public ComputeRoleDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }

  @Override
  public Future<ComputeRole> create(ComputeRole computeRole) {

    InsertQuery insertQuery = new InsertQuery();
    insertQuery.setTable(Constants.COMPUTE_ROLE_TABLE);
    insertQuery.setColumns(List.copyOf(computeRole.toNonEmptyFieldsMap().keySet()));
    insertQuery.setValues(List.copyOf(computeRole.toNonEmptyFieldsMap().values()));

    LOGGER.debug("Insert Query: {}", insertQuery.toSQL());
    LOGGER.debug("Query Params: {}", insertQuery.getQueryParams());

    return postgresService.insert(insertQuery)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(ComputeRole.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        LOGGER.error("Error inserting organization create request: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<List<ComputeRole>> getAll(Status status) {
    SelectQuery query = new SelectQuery(
      Constants.COMPUTE_ROLE_TABLE,
      List.of("*"),
      new Condition(Constants.STATUS, Condition.Operator.EQUALS, List.of(status.getStatus())),
      null, null, null, null
    );

    return postgresService.select(query)
      .compose(result -> {
        List<ComputeRole> requests = result.getRows().stream()
          .map(row -> ComputeRole.fromJson((JsonObject) row))
          .collect(Collectors.toList());
        return Future.succeededFuture(requests);
      })
      .recover(err -> {
        LOGGER.error("Error fetching requests by status: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }


  @Override
  public Future<Boolean> updateStatus(UUID requestId, Status status,UUID approvedBy) {
    UpdateQuery updateQuery = new UpdateQuery(
      Constants.COMPUTE_ROLE_TABLE,
      List.of(Constants.STATUS, Constants.UPDATED_AT,Constants.APPROVED_BY),
      List.of(status.getStatus(), Instant.now().toString(),approvedBy.toString()),
      new Condition(Constants.COMPUTE_ROLE_ID, Condition.Operator.EQUALS, List.of(requestId.toString())),
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
}
