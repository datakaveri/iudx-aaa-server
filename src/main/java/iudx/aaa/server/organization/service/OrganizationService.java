package iudx.aaa.server.organization.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.auditing.AuditingService;
import iudx.aaa.server.auditing.AuditingServiceVertxEBProxy;


import java.util.List;

@ProxyGen
@VertxGen
public interface OrganizationService {

  @GenIgnore
  static OrganizationService createProxy(Vertx vertx, String address) {
    return new OrganizationServiceVertxEBProxy(vertx, address) {
    };
  }

  Future<JsonObject> addOrganization(JsonObject request);

  Future<JsonObject> getOrganization(JsonObject request);

}
