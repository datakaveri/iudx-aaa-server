package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.util.Role;
import org.cdpg.dx.aaa.organization.util.Status;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrganizationCreateRequestDAOImpl implements OrganizationCreateRequestDAO {
  private final PostgresService postgresService;
  private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestDAOImpl.class);


  public OrganizationCreateRequestDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<OrganizationCreateRequest> create(OrganizationCreateRequest organizationCreateRequest) {
    Map<String, Object> orgCreateRequestMap = organizationCreateRequest.toJson().getMap();
    System.out.println("toJson() output: " + organizationCreateRequest.toJson().encodePrettily());

    orgCreateRequestMap.put(Constants.STATUS,"pending");

    List<String> columns = orgCreateRequestMap.keySet().stream().toList();
    List<Object> values = orgCreateRequestMap.values().stream().toList();

    System.out.println("Columns: " + columns);
    System.out.println("Values: " + values);

    System.out.println("Generated Columns: " + columns); // Make sure no empty or extra strings
    System.out.println("Column Count: " + columns.size());
    System.out.println("Value Count: " + values.size());

   InsertQuery query = new InsertQuery(Constants.ORG_CREATE_REQUEST_TABLE, columns, values);

    System.out.println("!!! "+query.toSQL());
    System.out.println("$$$$ "+query.getQueryParams());

    return postgresService.insert(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(OrganizationCreateRequest.fromJson(result.getRows().getJsonObject(0)));
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
        return Future.succeededFuture(OrganizationCreateRequest.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<Boolean> approve(UUID requestId, Status status) {

   return null;

  }

  @Override
  public Future<List<OrganizationCreateRequest>> getAll() {

    SelectQuery query = new SelectQuery(Constants.ORG_CREATE_REQUEST_TABLE,List.of("*"),null,null,null,null,null);

    return postgresService.select(query)
      .compose(result->
      {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        List<OrganizationCreateRequest> requests = result.getRows()
          .stream()
          .map(obj -> OrganizationCreateRequest.fromJson((JsonObject) obj))
          .collect(Collectors.toList());
        return Future.succeededFuture(requests);
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }


}






