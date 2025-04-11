package org.cdpg.dx.aaa.organization.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.Status;

import java.util.List;
import java.util.UUID;

public interface OrganizationJoinRequestDAO {
    Future<OrganizationJoinRequest> getById(UUID Id);

    Future<OrganizationJoinRequest> join(UUID organizationId, UUID userId);

    Future<List<OrganizationJoinRequest>> getAll(UUID orgId, Status status);

    Future<Boolean> updateStatus(UUID requestId, Status status);
}
