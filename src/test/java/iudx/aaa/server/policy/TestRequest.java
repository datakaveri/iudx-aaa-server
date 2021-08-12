package iudx.aaa.server.policy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;

import java.security.Provider;
import java.sql.Date;
import java.util.*;

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

  // Requests for Delete policy

  // res does not exist
  public static User allRolesUser = new User(new JsonObject().put("userId","a13eb955-c691-4fd3-b200-f18bc78810b5"));
  public static JsonObject obj1 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonObject obj2 = new JsonObject().put("id",UUID.randomUUID());
  public static JsonArray ResExistFail = new JsonArray().add(obj1).add(obj2);

  //res not owned by user
  public static User ProviderUser = new User(new JsonObject().put("userId","04617f23-7e5d-4118-8773-1b6c85da14ed")
          .put("roles",new JsonArray().add(Roles.PROVIDER)).put("keycloakId","04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonObject obj3 = new JsonObject().put("id","1e435fcb-11ce-4f4d-94c0-adf339932ba4");
  public static JsonArray ResOwnFail = new JsonArray().add(obj3);


  //no policy for delegate
  public static User DelegateUser = new User(new JsonObject().put("userId","04617f23-7e5d-4118-8773-1b6c85da14ed")
          .put("roles",new JsonArray().add(Roles.DELEGATE)).put("keycloakId","04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelPolFail = new JsonArray().add(obj3);

  //not a delegate
  public static User DelegateUserFail = new User(new JsonObject().put("userId","d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put("roles",new JsonArray().add(Roles.DELEGATE)).put("keycloakId","04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelFail = new JsonArray().add(obj3);


  //delete
  public static User successProvider = new User(new JsonObject().put("userId","a13eb955-c691-4fd3-b200-f18bc78810b5")
          .put("roles",new JsonArray().add(Roles.PROVIDER)).put("keycloakId","04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonObject obj4 = new JsonObject().put("id","36969790-b2ef-4784-936a-cac7f95eb960");
  public static JsonArray succDelReq = new JsonArray().add(obj4);


  public static String INSERT_REQ = "insert into test.policies " +
          "(user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) " +
          "values('32a4b979-4f4a-4c44-b0c3-2fe109952b5f','604cec16-0ba3-4eb9-bdcf-d2b98f1fddab','RESOURCE', " +
          " 'a13eb955-c691-4fd3-b200-f18bc78810b5','ACTIVE','2022-06-15 09:07:16.034289','{}', NOW(), NOW()) returning id";
}

