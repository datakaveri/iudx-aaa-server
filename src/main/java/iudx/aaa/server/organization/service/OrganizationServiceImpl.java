package iudx.aaa.server.organization.service;

import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import iudx.aaa.server.database.postgres.service.PostgresService;
import iudx.aaa.server.common.models.*;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Future;
import org.apache.maven.model.Organization;
import static iudx.aaa.server.organization.util.Constants.*;


public class OrganizationServiceImpl implements OrganizationService {


  private final PostgresService postgresService;
  private static final Logger LOGGER = LogManager.getLogger(OrganizationServiceImpl.class);


  public OrganizationServiceImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }


  @Override
  public Future<JsonObject> addOrganization(JsonObject request) {
    Promise<JsonObject> promise = Promise.promise();

    String id = request.getString("id");

    String query = ADD_ORGANIZATION_QUERY.replace("$1", "Organization")
      .replace("$2", NAME)
      .replace("$3", ID) + " VALUES (?, ?)";


    postgresService.executeQuery(query).onComplete(
      pgHandler -> {
        if (pgHandler.succeeded()) {
          promise.complete(pgHandler.result());
        } else {
          promise.fail(pgHandler.cause());
        }
    });
    return promise.future();
  }

  @Override
  public Future<JsonObject> getOrganization(JsonObject request) {
    Promise<JsonObject> promise = Promise.promise();
    String id = request.getString("id");

    String query = "SELECT * FROM Organization WHERE id = ?";

    postgresService.executeQuery(query).onComplete(
      pgHandler -> {
        if (pgHandler.succeeded()) {
          promise.complete(pgHandler.result());
        } else {
          promise.fail(pgHandler.cause());
        }
      });
    return promise.future();
  }

}
