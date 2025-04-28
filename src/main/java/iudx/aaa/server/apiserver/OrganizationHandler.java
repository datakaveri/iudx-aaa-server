package iudx.aaa.server.apiserver;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.aaa.server.apiserver.models.Response;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.models.User;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.apiserver.util.Constants.USER;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static java.util.Collections.copy;

public class OrganizationHandler {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationHandler.class);
    private final OrganizationService organizationService;
    private final KeycloakHandler keycloakHandler;

    public OrganizationHandler(OrganizationService organizationService, KeycloakHandler keycloakHandler){

        this.organizationService = organizationService;
        this.keycloakHandler = keycloakHandler;

    }

    public void updateOrganisationById(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        String idParam = String.valueOf(routingContext.pathParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);
        UpdateOrgDTO updateOrgDTO;

        updateOrgDTO = new UpdateOrgDTO(
                Optional.ofNullable(OrgRequestJson.getString("description")),
                OrgRequestJson.getString("org_name"),
                OrgRequestJson.getString("document_path"),
                Optional.empty()
        );

        organizationService.updateOrganizationById(orgId, updateOrgDTO)
                .onSuccess(updatedOrg -> processSuccess(routingContext, updatedOrg.toJson(), 200, "Updated Organisation Successfully"))
                .onFailure(err -> processFailure(routingContext, 500, "Failed to Update Organisation"));
    }

    public void deleteOrganisationById(RoutingContext routingContext) {

        String idParam = String.valueOf(routingContext.pathParam("id"));
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

        UUID requestId;
        Status status;

        JsonObject responseObject = OrgRequestJson.copy();
        responseObject.remove("status");

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));


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

        String idParam = routingContext.request().getParam("id");
        UUID orgId = UUID.fromString(idParam);

        organizationService.getOrganizationPendingJoinRequests(orgId)
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    List<Future> futures = new ArrayList<>();
                    for (OrganizationJoinRequest req : requests) {
                        JsonObject requestJson = req.toJson();
                        String keycloak_id = requestJson.getString("user_id");

                        Future<Void> future = keycloakHandler.getUsernameByKeycloakId(keycloak_id)
                                .onSuccess(userdetails -> {
                                    requestJson.put("requested_by_username", userdetails.getString("username"));
                                    requestJson.put("requested_by_email", userdetails.getString("email"));
                                })
                                .onFailure(err -> {
                                    LOGGER.error("Failed to fetch username for keycloak id: " + keycloak_id);
                                })
                                .mapEmpty();
                        futures.add(future);
                        jsonArray.add(requestJson);
                    }
                    CompositeFuture.all(futures)
                            .onSuccess(v -> processSuccess(routingContext, jsonArray, 200, "Retrieved Pending Join Requests"))
                            .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch usernames for pending join requests"));
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch pending join requests"));

    }

    public void joinOrganisationRequest(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationJoinRequest organizationJoinRequest;

        UUID OrgID;
        UUID UserID;

        User user = routingContext.get(USER);

        OrgID = UUID.fromString(OrgRequestJson.getString("org_id"));
        UserID = UUID.fromString(user.getUserId());

        organizationService.joinOrganizationRequest(OrgID, UserID)
                .onSuccess(createdRequest -> processSuccess(routingContext, createdRequest.toJson(), 201, "Created Join request"))
                .onFailure(err -> processFailure(routingContext, 500, "Internal Server Error: Failed to create Join request"));
    }

    public void approveOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();

        UUID requestId;
        Status status;

        requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
        status = Status.fromString(OrgRequestJson.getString("status"));

        JsonObject responseObject = OrgRequestJson.copy();
        responseObject.remove("status");


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
                    List<Future> futures = new ArrayList<>();

                    for (OrganizationCreateRequest req : requests) {
                        JsonObject requestJson = req.toJson();
                        String keycloak_id = requestJson.getString("requested_by");

                        Future<Void> future = keycloakHandler.getUsernameByKeycloakId(keycloak_id)
                                .onSuccess(userdetails -> {
                                    requestJson.put("requested_by_username", userdetails.getString("username"));
                                    requestJson.put("requested_by_email", userdetails.getString("email"));
                                })
                                .onFailure(err -> {
                                    LOGGER.error("Failed to fetch username for keycloak id: " + keycloak_id);
                                })
                                .mapEmpty();
                        futures.add(future);
                        jsonArray.add(requestJson);
                    }

                    CompositeFuture.all(futures)
                            .onSuccess(v -> processSuccess(routingContext, jsonArray, 200, "Retrieved Pending Create Requests"))
                            .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch usernames for pending create requests"));

                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch pending create requests"));

    }

    public void createOrganisationRequest(RoutingContext routingContext) {
        JsonObject OrgRequestJson = routingContext.body().asJsonObject();
        OrganizationCreateRequest organizationCreateRequest;

        User user = routingContext.get(USER);

        OrgRequestJson.put("requested_by", user.getUserId());

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

    public void deleteOrganisationUserById(RoutingContext routingContext) {

        String idParam = String.valueOf(routingContext.pathParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);

        JsonObject jsonRequest = routingContext.body().asJsonObject();
        UUID userId = UUID.fromString(jsonRequest.getString("user_id"));

        JsonObject responseObject = new JsonObject();

        organizationService.deleteOrganizationUser(orgId, userId)
                .onSuccess(deleted -> {
                    if(deleted){
                        processSuccess(routingContext, responseObject, 200, "Deleted Organisation User");
                    }
                    else {
                        processFailure(routingContext, 400, "Organisation User Not Found");
                    }
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to Delete Organisation User"));

    }

    public void getOrganisationUsers(RoutingContext routingContext) {

        String idParam = String.valueOf(routingContext.pathParam("id"));
        UUID orgId;

        orgId = UUID.fromString(idParam);

        organizationService.getOrganizationUsers(orgId)
                .onSuccess(requests -> {
                    JsonArray jsonArray = new JsonArray();
                    for (OrganizationUser req : requests) {
                        jsonArray.add(req.toJson());
                    }
                    processSuccess(routingContext, jsonArray, 200, "Retrieved Organisation Users");
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to fetch organisation users"));

    }

    public void updateOrganisationUserRole(RoutingContext routingContext) {

        JsonObject OrgRequestJson = routingContext.body().asJsonObject();

        String OrgidParam = String.valueOf(OrgRequestJson.getString("org_id"));
        UUID orgId;

        orgId = UUID.fromString(OrgidParam);

        String UseridParam = String.valueOf(OrgRequestJson.getString("user_id"));
        UUID user_id;

        user_id = UUID.fromString(UseridParam);

        Role role;
        role = Role.fromString(OrgRequestJson.getString("role"));

        UUID userId = UUID.fromString(OrgRequestJson.getString("user_id"));


      organizationService.updateUserRole(orgId,userId, role)
                .onSuccess(updated -> {
                    if(updated){
                        processSuccess(routingContext, new JsonObject(), 200, "Updated Organisation User Role");
                    }
                    else {
                        processFailure(routingContext, 400, "Organisation User Not Found");
                    }
                })
                .onFailure(err -> processFailure(routingContext, 500, "Failed to Update Organisation User Role"));

    }

    public Future<Void> processFailure(RoutingContext routingContext, int statusCode, String msg){

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
            return routingContext.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("type", "urn:dx:as:InternalServerError")
                            .put("title", "Internal Server Error")
                            .put("detail", msg)
                            .encode());
        }
    }

    public Future<Void> processSuccess(RoutingContext routingContext, JsonObject results, int statusCode, String msg){


        JsonObject response = new JsonObject()
                .put("type", "urn:dx:as:Success")
                .put("title",  msg)
                .put("results",  results);

        return routingContext.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    public Future<Void> processSuccess(RoutingContext routingContext, JsonArray results, int statusCode, String msg){


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
