package iudx.aaa.server.apiserver.service;

import java.util.List;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.dto.DeletePolicyRequestDTO;
import iudx.aaa.server.apiserver.dto.UserAccessRequestDTO;
import iudx.aaa.server.policy.PolicyService;

public class AccessAPIService {

  private PolicyService policyService;

  public AccessAPIService(PolicyService policyService) {
    this.policyService = policyService;
  }

  private JsonObject notImplemented() {
    return new JsonObject().put("status","Policy/access service verticle not implemented");
  }

  public Future<JsonObject> getAllAccessPolicies(String providerEmail) {
    Promise<JsonObject> promise = Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
  
  public Future<JsonObject> provideUserAccess2User(String providerEmail,List<UserAccessRequestDTO> userAccessDTO) {
    Promise<JsonObject> promise = Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
  
  public Future<JsonObject> deleteAccessPolicy(String providerEmail,List<DeletePolicyRequestDTO> deletePolicyDTO){
    Promise<JsonObject> promise=Promise.promise();
    promise.complete(notImplemented());
    return promise.future();
  }
}
