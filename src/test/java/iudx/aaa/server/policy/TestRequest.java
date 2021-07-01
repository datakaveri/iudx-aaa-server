package iudx.aaa.server.policy;

import io.vertx.core.json.JsonObject;

/**
 * Structures the JsonObject required for test cases.
 */
public class TestRequest {

  //Requests for verify policy
  public static JsonObject validVerifyPolicy = new JsonObject().put("itemId", "example.com/8d4b20ec4bf21efb363e72671e1b5bd77fd6cf91/resource-group")
          .put("itemType","resource_group")
          .put("role", "delegate")
          .put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36");

  public static JsonObject policyRoleFailure = new JsonObject().put("itemId", "example.com/8d4b20ec4bf21efb363e72671e1b5bd77fd6cf91/resource-group")
          .put("itemType","resource_group")
          .put("role", "consumer")
          .put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36");

  public static JsonObject policyFailure = new JsonObject().put("itemId", "example.com/8d4b20ec4bf21efb363e72671e1b5bd77fd6cfff/resource-group")
          .put("itemType","resource_group")
          .put("role", "delegate")
          .put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36");

}

