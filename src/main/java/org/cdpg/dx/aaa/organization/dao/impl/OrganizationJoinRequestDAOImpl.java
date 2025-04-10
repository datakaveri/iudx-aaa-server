package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class OrganizationJoinRequestDAOImpl implements OrganizationJoinRequestDAO{
  private final PostgresService postgresService;

  public OrganizationJoinRequestDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }



  @Override
  public Future<List<OrganizationJoinRequest>> getAll(UUID orgId)
  {

    SelectQuery query = new SelectQuery(Constants.ORG_JOIN_REQUEST_TABLE,List.of("*"),null,null,null,null,null);

    return postgresService.select(query)
      .compose(result->
      {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        List<OrganizationJoinRequest> requests = result.getRows()
          .stream()
          .map(obj -> OrganizationJoinRequest.fromJson((JsonObject) obj))
          .collect(Collectors.toList());
        return Future.succeededFuture(requests);
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });

  }

  @Override
  public Future<OrganizationJoinRequest>  join(UUID organizationId, UUID userId)
  {
      Map<String,Object> insertFields = new HashMap<>();

    insertFields.put(Constants.ORGANIZATION_ID,organizationId);
    insertFields.put(Constants.USER_ID,userId);
    insertFields.put(Constants.STATUS,"pending");
    insertFields.put(Constants.REQUESTED_AT, Instant.now().toString());

      List<String> columns = insertFields.keySet().stream().toList();
      List<Object> values = insertFields.values().stream().toList();

    InsertQuery query = new InsertQuery(Constants.ORG_JOIN_REQUEST_TABLE,columns,values);

    return postgresService.insert(query)
      .compose(result->{
        if(result.getRows().isEmpty())
        {
          return Future.failedFuture("Insert query failed");
        }
        return Future.succeededFuture(OrganizationJoinRequest.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err->{
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });

  }

  @Override
  public Future<Boolean> approve(UUID requestId, Status status) {

       return null;

  }

}






