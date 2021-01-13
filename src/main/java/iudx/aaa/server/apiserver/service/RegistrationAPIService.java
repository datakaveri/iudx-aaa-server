package iudx.aaa.server.apiserver.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.dto.RegistrationRequestDTO;
import iudx.aaa.server.registration.RegistrationService;
public class RegistrationAPIService {
  
  private RegistrationService registrationService;
  
  public RegistrationAPIService(RegistrationService registrationService) {
    this.registrationService=registrationService;
  }
  
  private JsonObject notImplemented() {
    return new JsonObject().put("status","registration service verticle not implemented");
  }
  
  /**
   * register a provider
   * @param registration
   * @return
   */
  public Future<JsonObject> providerRegistration(RegistrationRequestDTO registration){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
    
  }
  
  /**
   * register a consumer/provider/ingestor.
   * @param registration
   * @return
   */
  public Future<JsonObject> register(RegistrationRequestDTO registration){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
  /**
   * list all provider registration
   * @param filter
   * @return
   */
  public Future<JsonObject> getProviderRegistration(String filter){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
  /**
   * update a provider registration
   * @param userId
   * @param status
   * @return
   */
  public Future<JsonObject> updateProviderRegistration(String userId,String status){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }

}
