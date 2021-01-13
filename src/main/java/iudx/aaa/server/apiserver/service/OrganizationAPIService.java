package iudx.aaa.server.apiserver.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.dto.OrganizationRegistrationDTO;
import iudx.aaa.server.registration.RegistrationService;

public class OrganizationAPIService {
  
private RegistrationService registrationService;
  
  public OrganizationAPIService(RegistrationService registrationService) {
    this.registrationService=registrationService;
  }

  
  private JsonObject notImplemented() {
    return new JsonObject().put("status","registration service verticle not implemented");
  }
  /**
   * register an organization in IUDX-AAA
   * @param registration
   * @return
   */
  public Future<JsonObject> registerOrganization(OrganizationRegistrationDTO registration){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
  /**
   * list all organization registered in IUDX
   * @return
   */
  public Future<JsonObject> getAllRegisteredOrganizations(){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
}
