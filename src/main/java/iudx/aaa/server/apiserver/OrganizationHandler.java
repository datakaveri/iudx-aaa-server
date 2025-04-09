package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrganizationHandler {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationHandler.class);
    private final OrganizationService organizationService;

    public OrganizationHandler(OrganizationService organizationService){

        this.organizationService = organizationService;

    }

    public void updateOrganisationById(RoutingContext routingContext) {
    }

    public void deleteOrganisationById(RoutingContext routingContext) {

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
