package org.cdpg.dx.aaa.organization.dao;


import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.UpdateOrgCreateRequestDTO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.util.Status;

import java.util.List;
import java.util.UUID;

public interface OrganizationCreateRequestDAO {

  Future<OrganizationCreateRequest> create(OrganizationCreateRequest organizationCreateRequest);

  Future<OrganizationCreateRequest> getById(UUID requestId);

  Future<Boolean> approve(UUID id, Status status);

  Future<List<OrganizationCreateRequest>> getAll();
}
