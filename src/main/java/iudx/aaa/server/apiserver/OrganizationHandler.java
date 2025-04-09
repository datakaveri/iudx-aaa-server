package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.util.Status;

import java.util.Optional;
import java.util.UUID;

public class OrganizationHandler {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationHandler.class);
    private final OrganizationService organizationService;

    public OrganizationHandler(OrganizationService organizationService){

        this.organizationService = organizationService;

    }

    public void updateOrganisationById(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        UUID orgId;
        UpdateOrgDTO updateOrgDTO;

        orgId = UUID.fromString(OrgRequestJson.getString("org_id"));

        updateOrgDTO = new UpdateOrgDTO(
                UUID.fromString(OrgRequestJson.getString("org_id")),
                Optional.ofNullable(OrgRequestJson.getString("description")),
                OrgRequestJson.getString("org_name"),
                OrgRequestJson.getString("document_path"),
                Optional.ofNullable(OrgRequestJson.getString("updated_at"))
        );

        organizationService.updateOrganizationById(orgId, updateOrgDTO)
                .onSuccess(updatedOrg -> {
                    JsonObject response = new JsonObject()
                            .put("success", true)
                            .put("payload", updatedOrg.toJson());

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to update organization with ID: " + orgId, err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Internal Server Error")
                                    .encode());
                });



    }

    public void deleteOrganisationById(RoutingContext routingContext) {
        String idParam = String.valueOf(routingContext.queryParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);

        organizationService.deleteOrganization(orgId)
                .onSuccess(deleted -> {
                    JsonObject response = new JsonObject()
                            .put("success", deleted)
                            .put("message", deleted ? "Organization deleted successfully" : "Deletion failed");

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to delete organization with ID: " + idParam, err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Internal Server Error")
                                    .encode());
                });

    }

    public void listAllOrganisations(RoutingContext routingContext) {

    }

    public void approveJoinOrganisationRequests(RoutingContext routingContext) {

    }

    public void getJoinOrganisationRequests(RoutingContext routingContext) {

    }

    public void joinOrganisationRequest(RoutingContext routingContext) {

    }

    public void approveOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();

        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        organizationService.approveOrganizationCreateRequest(requestId, status)
                .onSuccess(approved -> {
                    JsonObject response = new JsonObject()
                            .put("success", approved)
                            .put("message", approved ? "Request approved successfully" : "Request could not be approved");

                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to approve organization creation request", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Internal Server Error")
                                    .encode());
                });

    }

    public void getOrganisationRequest(RoutingContext routingContext) {

    }

    public void createOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationCreateRequest organizationCreateRequest;
        try {
            organizationCreateRequest = new OrganizationCreateRequest(OrgRequestJson);
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Invalid request payload: " + e.getMessage()).encode());
            return;
        }

        organizationService.createOrganizationRequest(organizationCreateRequest)
                .onSuccess(createdRequest -> {
                    JsonObject response = new JsonObject()
                            .put("success", true)
                            .put("payload", createdRequest.toJson());

                    routingContext.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to create organization request", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Internal Server Error")
                                    .encode());
                });
    }
}
