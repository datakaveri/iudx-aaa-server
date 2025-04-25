package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class OrganizationUserDAOImpl implements OrganizationUserDAO {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationUserDAOImpl.class);

  private final PostgresService postgresService;

  public OrganizationUserDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

    @Override
    public Future<OrganizationUser> create(OrganizationUser orgUser) {
        // Get columns and values directly from the DTO map
        InsertQuery insertQuery = new InsertQuery();
        insertQuery.setTable(Constants.ORG_USER_TABLE);
        insertQuery.setColumns(List.copyOf(orgUser.toNonEmptyFieldsMap().keySet()));
        insertQuery.setValues(List.copyOf(orgUser.toNonEmptyFieldsMap().values()));

        LOGGER.debug("Insert Query: {}", insertQuery.toSQL());
        LOGGER.debug("Query Params: {}", insertQuery.getQueryParams());

        return postgresService.insert(insertQuery)
                .compose(result -> {
                    if (result.getRows().isEmpty()) {
                        return Future.failedFuture("Insert query returned no rows.");
                    }
                    return Future.succeededFuture(OrganizationUser.fromJson(result.getRows().getJsonObject(0)));
                })
                .recover(err -> {
                    LOGGER.error("Error inserting organization_user create request: {}", err.getMessage(), err);
                    return Future.failedFuture(err);
                });
  }

  @Override
  public Future<Boolean> updateRole(UUID orgId, UUID userId, Role role) {
    Condition conditions = new Condition(
            List.of(
                    new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId.toString())),
                    new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId.toString()))
            ),
            Condition.LogicalOperator.AND
    );

    UpdateQuery query = new UpdateQuery(
            Constants.ORG_USER_TABLE,
            List.of(Constants.ROLE),
            List.of(role.getRoleName()),
            new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId.toString())), //TODO: need to change
            null,
            null
    );

    return postgresService.update(query)
            .map(QueryResult::isRowsAffected)
            .recover(err -> {
              LOGGER.error("Failed to update role for user {} in organization {}: {}", userId, orgId, err.getMessage());
              return Future.succeededFuture(false);
            });
  }

  @Override
  public Future<Boolean> delete(UUID orgId, UUID userId) {

    DeleteQuery query = new DeleteQuery(Constants.ORG_USER_TABLE,  new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userId.toString())), null, null);

    return postgresService.delete(query)
            .map(QueryResult::isRowsAffected)
            .recover(err -> {
              LOGGER.error("Failed to delete user {} from organization {}: {}", userId, orgId, err.getMessage());
              return Future.succeededFuture(false);
            });
  }

  @Override
  public Future<Boolean> deleteUsersByOrgId(UUID orgId, List<UUID> uuids) {
    if (uuids == null || uuids.isEmpty()) {
      return Future.failedFuture("User IDs list is empty");
    }

    Condition conditions = new Condition(
            List.of(
                    new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId.toString())),
                    new Condition(Constants.USER_ID, Condition.Operator.IN, new ArrayList<>(uuids))
            ),
            Condition.LogicalOperator.AND
    );

    DeleteQuery query = new DeleteQuery(Constants.ORG_USER_TABLE,  new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgId)), null, null);

    return postgresService.delete(query)
            .map(QueryResult::isRowsAffected)
            .recover(err -> {
              LOGGER.error("Failed to delete users {} from organization {}: {}", uuids, orgId, err.getMessage());
              return Future.succeededFuture(false);
            });
  }

  @Override
  public Future<List<OrganizationUser>> getAll(UUID orgId) {
    Condition condition = new Condition(Constants.ORGANIZATION_ID,Condition.Operator.EQUALS,List.of(orgId.toString()));
    SelectQuery query = new SelectQuery(Constants.ORG_USER_TABLE, List.of("*"), condition, null, null, null, null);

    return postgresService.select(query)
            .compose(result -> {
              List<OrganizationUser> users = result.getRows().stream()
                      .map(row -> OrganizationUser.fromJson((JsonObject) row))
                      .collect(Collectors.toList());
              return Future.succeededFuture(users);
            })
            .recover(err -> {
              LOGGER.error("Error executing select query for organization {}: {}", orgId, err.getMessage());
              return Future.failedFuture(err);
            });
  }

  @Override
  public Future<Boolean> isOrgAdmin(UUID orgid, UUID userid) {
    Condition conditions = new Condition(
      List.of(
        new Condition(Constants.ORGANIZATION_ID, Condition.Operator.EQUALS, List.of(orgid.toString())),
        new Condition(Constants.USER_ID, Condition.Operator.EQUALS, List.of(userid.toString()))
      ),
      Condition.LogicalOperator.AND
    );
    SelectQuery query = new SelectQuery(Constants.ORG_USER_TABLE, List.of(Constants.ROLE), conditions, null, null, null, null);

    return postgresService.select(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("User is not part of this organization");
        }

        String roleStr = result.getRows().getJsonObject(0).getString(Constants.ROLE);
        if(roleStr.equals(Role.ADMIN.getRoleName()))
        {
          return Future.succeededFuture(true);
        } else {
          return Future.failedFuture("User is not an admin of this organization");
        }
      }).recover(err -> {
        LOGGER.error("Error executing select query for org {} and user {}: {}", orgid, userid, err.getMessage());
        return Future.failedFuture(err);
      });
  }
}
