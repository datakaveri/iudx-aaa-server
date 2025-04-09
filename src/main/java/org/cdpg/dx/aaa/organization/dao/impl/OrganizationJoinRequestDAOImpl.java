package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.util.Status;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
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
  public Future<List<OrganizationJoinRequest>> getRequests(UUID orgId)
  {
    SelectQuery query = new SelectQuery(Constants.ORG_JOIN_REQUEST_TABLE,null,null,null,null,null,null);

    return postgresService.select(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Select query returned no rows");
        }
        JsonArray rows = result.getRows();
        List<OrganizationJoinRequest> requests = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
          JsonObject row = rows.getJsonObject(i);
          requests.add(OrganizationJoinRequest.fromJson(row));
        }

        return Future.succeededFuture(requests);

      });

  }

  @Override
  public Future<OrganizationJoinRequest>  join(UUID organizationId, UUID userId)
  {
      Map<String,Object> updateFields = new HashMap<>();

      updateFields.put(Constants.ORGANIZATION_ID,organizationId);
      updateFields.put(Constants.USER_ID,userId);
      updateFields.put(Constants.STATUS,"pending");
      updateFields.put(Constants.REQUESTED_AT, Instant.now().toString());

      List<String> columns = updateFields.keySet().stream().toList();
      List<Object> values = updateFields.values().stream().toList();

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






