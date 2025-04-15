package iudx.aaa.server.apiserver;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.copy;

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
                .onSuccess(updatedOrg -> processSuccess(routingContext, updatedOrg.toJson(), 200, "Updated Organisation Successfully"))
                .onFailure(err -> processFailure(routingContext, 500, "Failed to Update Organisation"));
    }

    public void deleteOrganisationById(RoutingContext routingContext) {
        String idParam = String.valueOf(routingContext.queryParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);

        JsonObject responseObject = new JsonObject();

        organizationService.deleteOrganization(orgId)
                .onSuccess(deleted -> {
                    if(deleted){
                        processSuccess(routingContext, responseObject, 200, "Deleted Organisation");
                    }
                    else {
                        processFailure(routingContext, 400, "Organisation Not Found");
                    }
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to Delete Organisation"));

    }

    public void listAllOrganisations(RoutingContext routingContext) {

        organizationService.getOrganizations()
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (Organization req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    processSuccess(routingContext, jsonArray, 200, "Retrieved Organisations");
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch organisations"));

    }

    public void approveJoinOrganisationRequests(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        System.out.println(OrgRequestJson);
        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        JsonObject responseObject = (JsonObject) OrgRequestJson.copy().remove("status");

        organizationService.updateOrganizationJoinRequestStatus(requestId, status)
                .onSuccess(approved -> {
                    if(approved){
                        processSuccess(routingContext, responseObject, 200, "Approved Organisation Join Request");
                    }
                    else {
                        processFailure(routingContext, 400, "Request Not Found");
                    }
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to approve Organisation Join Request"));

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
                    processSuccess(routingContext, jsonArray, 200, "Retrieved Pending Join Requests");
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch pending join requests"));

    }

    public void joinOrganisationRequest(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationJoinRequest organizationJoinRequest;

        UUID OrgID;
        UUID UserID;

        OrgID = UUID.fromString(OrgRequestJson.getString("org_id"));
        UserID = UUID.fromString(OrgRequestJson.getString("user_id"));

        organizationService.joinOrganizationRequest(OrgID, UserID)
                .onSuccess(createdRequest -> processSuccess(routingContext, createdRequest.toJson(), 201, "Created Join request"))
                .onFailure(err -> processFailure(routingContext, 500, "Internal Server Error: Failed to create Join request"));
    }

    public void approveOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        System.out.println(OrgRequestJson);
        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        JsonObject responseObject = (JsonObject) OrgRequestJson.copy().remove("status");

        organizationService.updateOrganizationCreateRequestStatus(requestId, status)
                .onSuccess(approved -> {
                    if(approved){
                        processSuccess(routingContext, responseObject, 200, "Approved Organisation Create Request");
                    }
                    else {
                        processFailure(routingContext, 400, "Request Not Found");
                    }
                })
                .onFailure(err -> processFailure(routingContext, 500, "Internal Server Error"));

    }

    public void getOrganisationRequest(RoutingContext routingContext) {

        organizationService.getAllPendingOrganizationCreateRequests()
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (OrganizationCreateRequest req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    processSuccess(routingContext, jsonArray, 200, "Retrieved Pending Create Requests");
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch pending create requests"));

    }

    public void createOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationCreateRequest organizationCreateRequest;
        try {
          organizationCreateRequest = OrganizationCreateRequest.fromJson(OrgRequestJson);
        } catch (Exception e) {
            processFailure(routingContext, 400, "Invalid request payload: " + e.getMessage());
            return;
        }
        organizationService.createOrganizationRequest(organizationCreateRequest)
                .onSuccess(createdRequest -> processSuccess(routingContext, createdRequest.toJson(), 201, "Organisation Successfully Created"))
                .onFailure(err -> processFailure(routingContext, 500, "Internal Server Error"));
    }

    private Future<Void> processFailure(RoutingContext routingContext, int statusCode, String msg){
        
        if(statusCode == 400) {

            return routingContext.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("type", "urn:dx:as:MissingInformation")
                            .put("title", "Not Found")
                            .put("detail", msg)
                            .encode());
        } else if (statusCode == 401) {
            return routingContext.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("type", "urn:dx:as:InvalidAuthenticationToken")
                            .put("title", "Token Authentication Failed")
                            .put("detail", msg)
                            .encode());
        }
        else {
            return null;
        }
    }

    private Future<Void> processSuccess(RoutingContext routingContext, JsonObject results, int statusCode, String msg){

        JsonObject response = new JsonObject()
                .put("type", "urn:dx:as:Success")
                .put("title",  msg)
                .put("results",  results);

        return routingContext.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    private Future<Void> processSuccess(RoutingContext routingContext, JsonArray results, int statusCode, String msg){

        JsonObject response = new JsonObject()
                .put("type", "urn:dx:as:Success")
                .put("title",  msg)
                .put("results",  results);

        return routingContext.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }
}
