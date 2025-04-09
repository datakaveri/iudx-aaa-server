package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.util.Role;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class OrganizationUserDAOImpl implements OrganizationUserDAO{
  private final PostgresService postgresService;
  public OrganizationUserDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<Boolean> updateRole(UUID orgId, UUID userId, Role role) {
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(Constants.ROLE, role.getRoleName());
    updateFields.put(Constants.UPDATED_AT, Instant.now().toString());

    List<String> columns = updateFields.keySet().stream().toList();
    List<Object> values = updateFields.values().stream().toList();

//    Condition condition = new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId));
    Condition orgIdCondition = new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId));
    Condition userIdCondition = new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId));

    ConditionGroup groupCondition = new ConditionGroup(
      List.of(orgIdCondition, userIdCondition),
      ConditionGroup.LogicalOperator.AND
    );

    UpdateQuery query = new UpdateQuery(Constants.ORG_USER_TABLE, columns, values, groupCondition, null, null);

    return postgresService.update(query)
      .map(QueryResult::isRowsAffected)
      .recover(err->{
        System.out.println("Update failed: "+err.getMessage());
        return Future.succeededFuture(false);
      });

  }


  @Override
  public Future<Boolean> delete(UUID orgId, UUID userId)
  {
    Condition orgIdCondition = new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId));
    Condition userIdCondition = new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId));

    ConditionGroup groupCondition = new ConditionGroup(
      List.of(orgIdCondition, userIdCondition),
      ConditionGroup.LogicalOperator.AND
    );

    DeleteQuery query = new DeleteQuery(Constants.ORG_USER_TABLE,groupCondition,null,null);

    return postgresService.delete(query)
      .map(QueryResult::isRowsAffected)
      .recover(err->{
        System.out.println("Delete query failed: "+err.getMessage());
        return Future.succeededFuture(false);
      });

  }

  @Override
  public Future<Boolean> deleteUsersByOrgId(UUID orgId, ArrayList<UUID> uuids) {
    if (uuids == null || uuids.isEmpty()) {
      return Future.failedFuture("User IDs list is empty");
    }

    Condition orgCondition = new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId));
    Condition userIdInCondition = new Condition(Constants.USER_ID, Condition.Operator.IN, new ArrayList<>(uuids));

    ConditionGroup groupCondition = new ConditionGroup(
      List.of(orgCondition, userIdInCondition),
      ConditionGroup.LogicalOperator.AND
    );

    DeleteQuery query = new DeleteQuery(Constants.ORG_USER_TABLE,groupCondition,null,null);

    return postgresService.delete(query)
      .map(QueryResult::isRowsAffected)
      .recover(err->{
        System.out.println("Delete query failed: "+err.getMessage());
        return Future.succeededFuture(false);
      });

  }


  @Override
  public Future<List<OrganizationUser>> getAll(UUID orgId) {
    SelectQuery query = new SelectQuery(Constants.ORG_USER_TABLE,List.of("*"),null,null,null,null,null);

    return postgresService.select(query)
      .compose(result->
      {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("select query returned no rows.");
        }
        List<OrganizationUser> requests = result.getRows()
          .stream()
          .map(obj -> OrganizationUser.fromJson((JsonObject) obj))
          .collect(Collectors.toList());
        return Future.succeededFuture(requests);
      })
      .recover(err -> {
        System.err.println("Error select query : " + err.getMessage());
        return Future.failedFuture(err);
      });
  }



}
