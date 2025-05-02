package iudx.aaa.server.apiserver;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.aaa.server.apiserver.models.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.service.OrganizationService;

import java.util.UUID;

import static iudx.aaa.server.apiserver.util.Constants.USER;

public class CreditHandler {

    private static final Logger LOGGER = LogManager.getLogger(CreditHandler.class);
    private final CreditService creditService;

    public CreditHandler(CreditService creditService){

        this.creditService = creditService;

    }

    public void getCreditRequestByStatus(RoutingContext routingContext){
//        String status = routingContext.queryParam("status").get(0);
//        LOGGER.info("Received request to get credit requests with status: {}", status);
//
//        if (status == null || status.isEmpty()) {
//            LOGGER.error("Status parameter is missing or empty");
//            processFailure(routingContext, 400 , "Status parameter is required");
//            return;
//        }

        creditService.getAllPendingRequests()
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        JsonArray jsonArray = new JsonArray();
                        for (CreditRequest req : ar.result()) {
                            jsonArray.add(req.toJson());
                        }
                        processSuccess(routingContext, jsonArray, 200, "Credit requests fetched successfully");
                    } else {
                        LOGGER.error("Failed to get credit requests: {}", ar.cause().getMessage());
                        processFailure(routingContext, 500, ar.cause().getMessage());
                    }
                });
    }

    public void CreateCreditRequest(RoutingContext routingContext) {
        JsonObject creditRequestJson = routingContext.getBodyAsJson();
        LOGGER.info("Received request to create credit request: {}", creditRequestJson);

        if (creditRequestJson == null || !creditRequestJson.containsKey("amount")) {
            LOGGER.error("Invalid request body");
            processFailure(routingContext, 400, "Invalid request body");
            return;
        }

        User user = routingContext.get(USER);
        creditRequestJson.put("user_id", user.getUserId());


      CreditRequest creditRequest = CreditRequest.fromJson(creditRequestJson);
      String amountStr = creditRequestJson.getString("amount");

        if (amountStr == null || amountStr.isEmpty()) {
            LOGGER.error("Amount is missing or empty in the request body");
            processFailure(routingContext, 400, "Amount is required and must be a valid number");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid amount format: {}", amountStr);
            processFailure(routingContext, 400, "Amount must be a valid number");
            return;
        }

        creditService.createCreditRequest(creditRequest)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        processSuccess(routingContext, ar.result().toJson(), 201, "Credit request created successfully");
                    } else {
                        LOGGER.error("Failed to create credit request: {}", ar.cause().getMessage());
                        processFailure(routingContext, 500, ar.cause().getMessage());
                    }
                });
    }

    public void UpdateCreditRequest(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.getBodyAsJson();
        LOGGER.info("Received request to update credit request: {}", requestBody);

        if (requestBody == null || !requestBody.containsKey("req_id") || !requestBody.containsKey("status")) {
            LOGGER.error("Invalid request body");
            processFailure(routingContext, 400, "Invalid request body");
            return;
        }

        User transactedByUser = routingContext.get(USER);

        String transactedByIdStr = transactedByUser.getUserId();
        String requestIdStr = requestBody.getString("req_id");
        String statusStr = requestBody.getString("status");

        if (requestIdStr == null || requestIdStr.isEmpty() || statusStr == null || statusStr.isEmpty()) {
            LOGGER.error("Request ID or status is missing or empty in the request body");
            processFailure(routingContext, 400, "Request ID and status are required");
            return;
        }

        UUID requestId,transactedById;
        try {
            requestId = UUID.fromString(requestIdStr);
            transactedById = UUID.fromString(transactedByIdStr);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid request ID format: {}", requestIdStr);
            processFailure(routingContext, 400, "Request ID must be a valid UUID");
            return;
        }

        creditService.updateCreditRequestStatus(requestId, Status.fromString(statusStr),transactedById)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        processSuccess(routingContext, new JsonObject().put("updated", ar.result()), 200, "Credit request updated successfully");
                    } else {
                        LOGGER.error("Failed to update credit request: {}", ar.cause().getMessage());
                        processFailure(routingContext, 500, ar.cause().getMessage());
                    }
                });
    }

    public void GetUserCreditBalance(RoutingContext routingContext) {
        String idParam = String.valueOf(routingContext.pathParam("id"));

        creditService.getBalance(UUID.fromString(idParam))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        processSuccess(routingContext,new JsonObject().put("balance", ar.result()), 200, "User credit balance fetched successfully");
                    } else {
                        LOGGER.error("Failed to get user credit balance: {}", ar.cause().getMessage());
                        processFailure(routingContext, 500, ar.cause().getMessage());
                    }
                });
    }

    public void DeductUserCredit(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.getBodyAsJson();
        LOGGER.info("Received request to deduct credit: {}", requestBody);

        if (requestBody == null || !requestBody.containsKey("amount")) {
            LOGGER.error("Invalid request body");
            processFailure(routingContext, 400, "Invalid request body");
            return;
        }

        String amountStr = requestBody.getString("amount");

        User user = routingContext.get(USER);
        requestBody.put("transacted_by",user.getUserId());

        CreditTransaction creditTransaction = CreditTransaction.fromJson(requestBody);


        if (amountStr == null || amountStr.isEmpty()) {
            LOGGER.error("Amount is missing or empty in the request body");
            processFailure(routingContext, 400, "Amount is required and must be a valid number");
            return;
        }
        try {
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid amount format: {}", amountStr);
            processFailure(routingContext, 400, "Amount must be a valid number");
            return;
        }

        creditService.deductCredits(creditTransaction)
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        processSuccess(routingContext, new JsonObject(), 200, "Credit deducted successfully");
                    } else {
                        LOGGER.error("Failed to deduct credit: {}", ar.cause().getMessage());
                        processFailure(routingContext, 500, ar.cause().getMessage());
                    }
                });
    }

//    public void GetOtherUserCreditBalance(RoutingContext routingContext) {
//        JsonObject requestBody = routingContext.getBodyAsJson();
//        LOGGER.info("Received request to get other user credit balance: {}", requestBody);
//
//        if (requestBody == null || !requestBody.containsKey("user_id")) {
//            LOGGER.error("Invalid request body");
//            processFailure(routingContext, 400, "Invalid request body");
//            return;
//        }
//
//        String userIdStr = requestBody.getString("user_id");
//
//        if (userIdStr == null || userIdStr.isEmpty()) {
//            LOGGER.error("User ID is missing or empty in the request body");
//            processFailure(routingContext, 400, "User ID is required");
//            return;
//        }
//
//        creditService.getCreditByUserId(UUID.fromString(userIdStr))
//                .onComplete(ar -> {
//                    if (ar.succeeded()) {
//                        processSuccess(routingContext, ar.result().toJson(), 200, "Other user credit balance fetched successfully");
//                    } else {
//                        LOGGER.error("Failed to get other user credit balance: {}", ar.cause().getMessage());
//                        processFailure(routingContext, 500, ar.cause().getMessage());
//                    }
//                });
//    }

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
