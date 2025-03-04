package iudx.aaa.server.organization.controlller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import iudx.aaa.server.common.models.response.FailureResponseHandler;
import iudx.aaa.server.common.models.response.ResponseType;
import iudx.aaa.server.common.models.response.SuccessResponseHandler;
import iudx.aaa.server.organization.service.OrganizationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class OrganizationController extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationController.class);
  private final OrganizationService organizationService;

  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @Override
  public void start() {
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());


    router.get("/organizations").handler(this::getOrganizations);
    router.post("/organizations").handler(this::addOrganization);

    server.requestHandler(router).listen(config().getInteger("port", 8083), res -> {
      if (res.succeeded()) {
        LOGGER.info("HTTP server started on port " + config().getInteger("port", 8083));
      } else {
        LOGGER.info("Failed to start HTTP server: " + res.cause().getMessage());
      }
    });

  }

  private void addOrganization(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    JsonObject requestBody = routingContext.getBodyAsJson();

    if (requestBody == null) {
      LOGGER.error("Request body is missing or not valid JSON");
      FailureResponseHandler.processBackendResponse(response, "Request body is missing or not valid JSON");
      return;
    }

    // Directly pass JsonObject to the service
    organizationService.addOrganization(requestBody).onComplete(ar -> {
      if (ar.succeeded()) {
        SuccessResponseHandler.handleSuccessResponse(
          response, ResponseType.Created.getCode(), ar.result()
        );
      } else {
        LOGGER.error("Failed to add organization: " + ar.cause().getMessage());
        FailureResponseHandler.processBackendResponse(response, ar.cause().getMessage());
      }
    });
  }

  private void getOrganizations(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    JsonObject request = new JsonObject();  // Create an empty request if no params are needed

    organizationService.getOrganization(request).onComplete(ar -> {
      if (ar.succeeded()) {
        JsonObject result = ar.result();
        JsonArray org = result.getJsonArray("organizations");

        if (org == null || org.isEmpty()) {
          SuccessResponseHandler.handleSuccessResponse(
            response, ResponseType.NoContent.getCode(), (JsonArray) null
          );
        } else {
          SuccessResponseHandler.handleSuccessResponse(
            response, ResponseType.Ok.getCode(), new JsonObject().put("organizations", org)
          );
        }
      } else {
        LOGGER.error("Failed to retrieve organizations: " + ar.cause().getMessage());
        FailureResponseHandler.processBackendResponse(response, ar.cause().getMessage());
      }
    });
  }
}



