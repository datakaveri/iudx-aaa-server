package iudx.aaa.server.policy;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Structures the JsonObject required for test cases.
 */
public class TestRequest {

  //Requests for verify policy
  public static JsonObject validVerifyPolicy = new JsonObject().put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType","resource_group")
          .put("role", "delegate")
          .put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36");

  public static JsonObject policyRoleFailure = new JsonObject().put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType","resource_group")
          .put("role", "consumer")
          .put("userId","d03dba66-cf23-473f-93fd-db0a26265ef");

  public static JsonObject policyFailure = new JsonObject().put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType","resource_group")
          .put("role", "consumer")
          .put("userId","e71ba781-78c1-4de1-ab62-65c19dc9ef55");

  //Requests for List Policy
  //List as a provider
  public static JsonObject validListPolicyProvider = new JsonObject().put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36");

  //List as a consumer
  public static JsonObject validListPolicyConsumer = new JsonObject().put("userId","41afd50c-f1d8-40e3-8a6b-f983f2f477ad");

  //no policy exists
  public static JsonObject invalidListPolicy = new JsonObject().put("userId","96a6ca1c-a253-446a-9175-8cd075683a0a");

}

