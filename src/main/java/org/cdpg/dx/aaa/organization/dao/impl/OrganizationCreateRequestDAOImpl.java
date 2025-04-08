package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.util.Status;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrganizationCreateRequestDAOImpl implements OrganizationCreateRequestDAO {
  private final PostgresService postgresService;
  private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestDAOImpl.class);


  public OrganizationCreateRequestDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<OrganizationCreateRequest> create(OrganizationCreateRequest organizationCreateRequest) {
    Map<String, Object> orgCreateRequestMap = organizationCreateRequest.toJson().getMap();

    List<String> columns = orgCreateRequestMap.keySet().stream().toList();
    List<Object> values = orgCreateRequestMap.values().stream().toList();

    System.out.println("Columns: " + columns);
    System.out.println("Values: " + values);

    InsertQuery query = new InsertQuery(Constants.ORG_CREATE_REQUEST_TABLE, columns, values);

    return postgresService.insert(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(new OrganizationCreateRequest(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return io.vertx.core.Future.failedFuture(err);
      });

  }

  @Override
  public Future<OrganizationCreateRequest> getById(UUID id) {

    List<String> columns = Constants.ALL_ORG_CREATE_REQUEST_FIELDS;
    ;
    // Create Condition for WHERE clause
    Condition condition = new Condition(Constants.ORG_CREATE_ID, Condition.Operator.EQUALS, List.of(id));


    SelectQuery query = new SelectQuery(Constants.ORG_CREATE_REQUEST_TABLE, columns, condition, null, null, null, null);

    return postgresService.select(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        return Future.succeededFuture(new OrganizationCreateRequest(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<Boolean> approve(UUID requestId, Status status) {

    Map<String, Object> updateFields = new HashMap<>();

    updateFields.put(Constants.STATUS, status);
    updateFields.put(Constants.UPDATED_AT, Instant.now().toString()); // Optional: Track the update time

    if (updateFields.isEmpty()) {
      return Future.failedFuture(new IllegalArgumentException("No fields to update"));
    }

    Condition condition = new Condition(Constants.ORG_CREATE_ID, Condition.Operator.EQUALS, List.of(requestId));
    List<String> columns = updateFields.keySet().stream().toList();
    List<Object> values = updateFields.values().stream().toList();
    UpdateQuery query = new UpdateQuery(Constants.ORG_CREATE_REQUEST_TABLE, columns, values, condition, null, null);

    return postgresService.update(query)
      .map(result->result.isRowsAffected())
      .recover(err->{
        System.out.println("Update failed: "+err.getMessage());
        return Future.succeededFuture(false);
      });
  }

  @Override
  public Future<List<OrganizationCreateRequest>> getAll() {
    return null;
  }
}






