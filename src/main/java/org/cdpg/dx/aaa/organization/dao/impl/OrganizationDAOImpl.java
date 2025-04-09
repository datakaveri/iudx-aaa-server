package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrganizationDAOImpl implements OrganizationDAO {

  private final PostgresService postgresService;

  public OrganizationDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<Organization> create(Organization organization) {
    Map<String, Object> organizationMap = organization.toNonEmptyFieldsMap();

    List<String> columns = organizationMap.keySet().stream().toList();
    List<Object> values = organizationMap.values().stream().toList();

    InsertQuery query = new InsertQuery(Constants.ORGANIZATION_TABLE, columns, values);

    return postgresService.insert(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.err.println("Error inserting policy: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }

  //UPDATE ORG TABLE , ORG USER TABLE , ORG JOIN REQUEST
  @Override
  public  Future<Organization> update(UUID id, UpdateOrgDTO updateOrgDTO) {
    Map<String, Object> updateFields = updateOrgDTO.toNonEmptyFieldsMap();

    List<String> columns = updateFields.keySet().stream().toList();
    List<Object> values = updateFields.values().stream().toList();

    Condition condition = new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(id));

    // Build the UpdateQuery
    UpdateQuery query = new UpdateQuery(Constants.ORGANIZATION_TABLE, columns, values, condition, null, null);

    return postgresService.update(query).compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Update query returned no rows");
        }
        return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.out.println("Error inserting policy " + err.getMessage());
        return Future.failedFuture(err);
      });

  }


  @Override
  public Future<Organization> get(UUID orgId) {

    List<String> columns = Constants.ALL_ORG_FIELDS;;
    // Create Condition for WHERE clause
    Condition condition = new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(orgId));


    SelectQuery query = new SelectQuery(Constants.ORGANIZATION_TABLE, columns,condition,null, null,null,null);

    return postgresService.select(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        return Future.succeededFuture(Organization.fromJson(result.getRows().getJsonObject(0)));
      })
      .recover(err -> {
        System.err.println("Error getting organization: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<Boolean> delete(UUID id) {

    // Create Condition for WHERE clause
    Condition condition = new Condition(Constants.ORG_ID, Condition.Operator.EQUALS, List.of(id));

    // Build the DeleteQuery
    DeleteQuery query = new DeleteQuery(Constants.ORGANIZATION_TABLE, condition, null, null);


    return postgresService.delete(query)
      .compose(result -> {
        if (!result.isRowsAffected()) {
          return Future.failedFuture("No rows updated");
        }
        return Future.succeededFuture(true);
      })
      .recover(err -> {
        System.err.println("Error updating policy: "+ id.toString() + " msg:"+ err.getMessage());
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<List<Organization>> getAll() {
    SelectQuery query = new SelectQuery(Constants.ORGANIZATION_TABLE,List.of("*"),null,null,null,null,null);

    return postgresService.select(query)
      .compose(result->
      {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        List<Organization> requests = result.getRows()
          .stream()
          .map(obj -> Organization.fromJson((JsonObject) obj))
          .collect(Collectors.toList());
        return Future.succeededFuture(requests);
      })
      .recover(err -> {
        System.err.println("Error inserting create request: " + err.getMessage());
        return Future.failedFuture(err);
      });
  }

}
