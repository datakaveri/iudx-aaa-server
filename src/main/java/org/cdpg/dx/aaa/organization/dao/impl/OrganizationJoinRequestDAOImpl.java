package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.util.Status;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;

public class OrganizationJoinRequestDAOImpl implements OrganizationJoinRequestDAO{
  private final PostgresService postgresService;

  public OrganizationJoinRequestDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<OrganizationJoinRequest> join(UUID organizationId, UUID userId) {
    return null;
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getRequests(UUID orgId) {
    return null;
  }

  @Override
  public Future<Boolean> approve(UUID requestId, Status status) {
    return null;
  }
}




