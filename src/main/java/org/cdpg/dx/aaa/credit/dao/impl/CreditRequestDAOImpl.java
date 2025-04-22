package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationCreateRequestDAOImpl;
import org.cdpg.dx.aaa.organization.models.Organization;
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

public class CreditRequestDAOImpl implements CreditRequestDAO {
  private final PostgresService postgresService;
  private static final Logger LOGGER = LogManager.getLogger(CreditRequestDAOImpl.class);


  public CreditRequestDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }

  @Override
  public Future<CreditRequest> create(UUID userId, double amount) {
    InsertQuery insertQuery = new InsertQuery(
      Constants.CREDIT_REQUEST_TABLE,
      List.of(Constants.USER_ID, Constants.AMOUNT,Constants.STATUS),
      List.of(userId.toString(), amount,"pending"));


    LOGGER.debug("Insert Query: {}", insertQuery.toSQL());
    LOGGER.debug("Query Params: {}", insertQuery.getQueryParams());

    return postgresService.insert(insertQuery)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(CreditRequest.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        LOGGER.error("Error inserting organization create request: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }


  @Override
  public Future<CreditRequest> getById(UUID requestId) {
    SelectQuery selectQuery = new SelectQuery(
      Constants.CREDIT_REQUEST_TABLE,
      Constants.ALL_CREDIT_REQUEST_FIELDS,
      new Condition(Constants.CREDIT_REQUEST_ID, Condition.Operator.EQUALS, List.of(requestId.toString())),
      null, null, null, null
    );

    return postgresService.select(selectQuery)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("No request found with the given ID.");
        }
        return Future.succeededFuture(CreditRequest.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        LOGGER.error("Error fetching request by ID: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<List<CreditRequest>> getAll(Status status) {

    Condition condition = new Condition(Constants.STATUS , Condition.Operator.EQUALS, List.of(status.getStatus()));

    SelectQuery query = new SelectQuery(
      Constants.CREDIT_REQUEST_TABLE,
      List.of("*"),
      condition, null, null, null, null
    );

    return postgresService.select(query)
      .compose(result -> {
        List<CreditRequest> creditRequests = result.getRows().stream()
          .map(row -> CreditRequest.fromJson((JsonObject) row))
          .collect(Collectors.toList());
        return Future.succeededFuture(creditRequests);
      })
      .recover(err -> {
        LOGGER.error("Error fetching all organizations: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<Boolean> updateStatus(UUID requestId, Status status) {
    UpdateQuery updateQuery = new UpdateQuery(
      Constants.CREDIT_REQUEST_TABLE,
      List.of(Constants.STATUS, Constants.UPDATED_AT),
      List.of(status.getStatus(), Instant.now().toString()),
      new Condition(Constants.CREDIT_REQUEST_ID, Condition.Operator.EQUALS, List.of(requestId.toString())),
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
