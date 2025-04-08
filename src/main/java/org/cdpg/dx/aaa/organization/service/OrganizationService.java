package org.cdpg.dx.aaa.organization.service;


import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.util.Role;
import org.cdpg.dx.aaa.organization.util.Status;


import java.util.List;
import java.util.UUID;

public interface OrganizationService {


  Future<OrganizationCreateRequest> getOrganizationCreateRequests(UUID requestId);

  Future<List<OrganizationCreateRequest>> getAllOrganizationCreateRequests();

  Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest organizationCreateRequest);

  // status enum , adding a new org entry and updating orgCreateRequest
  // update orgUser role
  Future<Boolean> approveOrganizationCreateRequest(UUID requestId, Status status);

  Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role Role);

  Future<OrganizationJoinRequest> joinOrganizationRequest(UUID organizationId, UUID userId);

  Future<List<OrganizationJoinRequest>> getOrganizationJoinRequests(UUID orgId);

  Future<Boolean> approveOrganizationJoinRequest(UUID requestId,Status Status);

  Future<Boolean> deleteOrganization(UUID orgId);

  Future<List<Organization>> getOrganizations();

  Future<Organization> getOrganizationById(UUID orgId);

  //update organization
  Future<Organization> updateOrganizationById(UUID orgId,UpdateOrgDTO updateOrgDTO);

  //delete from orgUser Table
  Future<Boolean> deleteOrganizationUser(UUID orgId,UUID userId);

  //delete from orgUser Table
  Future<Boolean> deleteOrganizationUsers(UUID orgId,List<UUID> userId);

  Future<List<OrganizationUser>> getOrganizationUsers(UUID orgId);

  // to check - info about the users
 // Future<OrganizationUser> getOrganizationUserById(UUID userId);


}

