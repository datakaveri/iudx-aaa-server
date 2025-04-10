package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.dao.*;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.glassfish.jaxb.runtime.v2.runtime.reflect.opt.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.cdpg.dx.aaa.organization.models.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationServiceImpl.class);
    private final OrganizationCreateRequestDAO organizationCreateRequestDAO;
    private final OrganizationUserDAO organizationUserDAO;
    private final OrganizationDAO organizationDAO;
    private final OrganizationJoinRequestDAO organizationJoinRequestDAO;


    public OrganizationServiceImpl(OrganizationDAOFactory organizationDAOFactory) {
        this.organizationCreateRequestDAO = organizationDAOFactory.organizationCreateRequest();
        this.organizationUserDAO = organizationDAOFactory.organizationUserDAO();
        this.organizationJoinRequestDAO = organizationDAOFactory.organizationJoinRequestDAO();
        this.organizationDAO = organizationDAOFactory.organizationDAO();
    }

    @Override
    public Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest organizationCreateRequest) {

        return organizationCreateRequestDAO.create(organizationCreateRequest);
    }

    public Future<List<OrganizationCreateRequest>> getAllOrganizationCreateRequests() {
        return organizationCreateRequestDAO.getAll();
    }

    @Override
    public Future<Boolean> approveOrganizationCreateRequest(UUID requestId, Status status) {
      return organizationCreateRequestDAO.approve(requestId, status)
      .compose(updated->{
        if(!updated)
        {
          return Future.succeededFuture(false);
        }
        if(status.getStatus().equalsIgnoreCase("approved"))
        {
          return organizationCreateRequestDAO.getById(requestId)
          .compose(createRequest->{

            JsonObject jsonObject = new JsonObject();
            jsonObject.put(Constants.ORG_NAME,createRequest.name());
            jsonObject.put(Constants.ORG_DESCRIPTION,createRequest.description());
            jsonObject.put(Constants.DOCUMENTS_PATH,createRequest.documentPath());

            Organization organization = Organization.fromJson(jsonObject);

            return organizationDAO.create(organization)
              .map(org -> true);
          });
        }
        else {
          return Future.succeededFuture(true);
        }
      });
    }

    @Override
    public Future<OrganizationCreateRequest> getOrganizationCreateRequests(UUID requestId) {
        return organizationCreateRequestDAO.getById(requestId);
    }

    @Override
    public Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role role) {

        return organizationUserDAO.updateRole(orgId, userId, role);
    }

    @Override
    public Future<OrganizationJoinRequest> joinOrganizationRequest(UUID organizationId, UUID userId) {
        return organizationJoinRequestDAO.join(organizationId, userId);
    }

    @Override
    public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequests(UUID orgId) {
        return organizationJoinRequestDAO.getAll(orgId);
    }

    @Override
    public Future<Boolean> approveOrganizationJoinRequest(UUID requestId, Status status) {
       return organizationJoinRequestDAO.approve(requestId, status);
    }

    @Override
    public Future<Boolean> deleteOrganization(UUID orgId) {
        return organizationDAO.delete(orgId);
    }

    @Override
    public Future<List<Organization>> getOrganizations() {
        return organizationDAO.getAll();
    }

    @Override
    public Future<Organization> getOrganizationById(UUID orgId) {
        return organizationDAO.get(orgId);
    }

    @Override
    public Future<Organization> updateOrganizationById(UUID orgId, UpdateOrgDTO updateOrgDTO) {
        return organizationDAO.update(orgId, updateOrgDTO);
    }

    @Override
    public Future<Boolean> deleteOrganizationUser(UUID orgId, UUID userId) {
        return organizationUserDAO.delete(orgId, userId);
    }

    @Override
    public Future<Boolean> deleteOrganizationUsers(UUID orgId, List<UUID> userId) {
        return organizationUserDAO.deleteUsersByOrgId(orgId, new ArrayList<>(userId));
    }

    @Override
    public Future<List<OrganizationUser>> getOrganizationUsers(UUID orgId) {
        return organizationUserDAO.getAll(orgId);
    }


}


