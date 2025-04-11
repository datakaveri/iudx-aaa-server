package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        organizationService.getOrganizations()
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (Organization req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(jsonArray.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch all org", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Failed to fetch records").encode());
                });


    }

    public void approveJoinOrganisationRequests(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        System.out.println(OrgRequestJson);
        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        organizationService.updateOrganizationJoinRequestStatus(requestId, status)
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

    public void getJoinOrganisationRequests(RoutingContext routingContext) {

        String idParam = String.valueOf(routingContext.queryParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);

        organizationService.getOrganizationPendingJoinRequests(orgId)
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (OrganizationJoinRequest req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(jsonArray.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch all org join requests", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Failed to fetch records").encode());
                });


    }

    public void joinOrganisationRequest(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationJoinRequest organizationJoinRequest;

        UUID OrgID;
        UUID UserID;

        OrgID = UUID.fromString(OrgRequestJson.getString("org_id"));
        UserID = UUID.fromString(OrgRequestJson.getString("user_id"));

        organizationService.joinOrganizationRequest(OrgID, UserID)
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
                    LOGGER.error("Failed to create organization join request", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("success", false)
                                    .put("error", "Internal Server Error")
                                    .encode());
                });


    }

    public void approveOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        System.out.println(OrgRequestJson);
        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        organizationService.updateOrganizationCreateRequestStatus(requestId, status)
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

        organizationService.getAllPendingOrganizationCreateRequests()
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (OrganizationCreateRequest req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    routingContext.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(jsonArray.encode());
                })
                .onFailure(err -> {
                    LOGGER.error("Failed to fetch all org create requests", err);
                    routingContext.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Failed to fetch records").encode());
                });

    }

    public void createOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationCreateRequest organizationCreateRequest;
        try {
          organizationCreateRequest = OrganizationCreateRequest.fromJson(OrgRequestJson);
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
                            .put("payload",  new JsonObject(createdRequest.toNonEmptyFieldsMap()));

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
