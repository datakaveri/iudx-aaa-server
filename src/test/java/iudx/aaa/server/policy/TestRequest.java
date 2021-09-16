package iudx.aaa.server.policy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;

import java.util.List;
import java.util.UUID;

/** Structures the JsonObject required for test cases. */
public class TestRequest {

  // Requests for verify policy

  public static JsonObject roleFailure =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject resFailure =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group/123")
          .put("itemType", "resource")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject resGrpFailure =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220b/resource-group")
          .put("itemType", "resource_group")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject consumerVerification =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "41afd50c-f1d8-40e3-8a6b-f983f2f477ad");

  public static JsonObject providerUserFailure =
      new JsonObject()
          .put(
              "itemId",
              "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220b/catalogue.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject NoCataloguePolicy =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/f8dc5bbb151968101ad4596819a248d3e0ea20c0/catalogue.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject validProviderCat =
      new JsonObject()
          .put(
              "itemId",
              "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/catalogue.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject validProviderVerification =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType", "resource_group")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject invalidCatDelegate =
      new JsonObject()
          .put(
              "itemId",
              "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/catalogue.iudx.io/catalogue/crud")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject invalidDelegate =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject NoCatalogueProviderPolicy =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/f8dc5bbb151968101ad4596819a248d3e0ea20c0/catalogue.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject validDelegateVerification =
      new JsonObject()
          .put("itemId", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36");

  // Requests for List Policy
  // List as a provider
  public static User validListPolicyProvider =
      new User(new JsonObject().put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36"));

  // List as a consumer
  public static User validListPolicyConsumer =
      new User(new JsonObject().put("userId", "41afd50c-f1d8-40e3-8a6b-f983f2f477ad"));

  // no policy exists
  public static User invalidListPolicy =
      new User(new JsonObject().put("userId", "96a6ca1c-a253-446a-9175-8cd075683a0a"));

  // Requests for Delete policy

  // res does not exist
  public static User allRolesUser =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.ADMIN)));
  public static JsonObject obj1 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonObject obj2 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonArray ResExistFail = new JsonArray().add(obj1).add(obj2);

  // res not owned by user
  public static User ProviderUser =
      new User(
          new JsonObject()
              .put("userId", "04617f23-7e5d-4118-8773-1b6c85da14ed")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonObject obj3 =
      new JsonObject().put("id", "1e435fcb-11ce-4f4d-94c0-adf339932ba4");
  public static JsonArray ResOwnFail = new JsonArray().add(obj3);

  // no policy for delegate
  public static User DelegateUser =
      new User(
          new JsonObject()
              .put("userId", "04617f23-7e5d-4118-8773-1b6c85da14ed")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelPolFail = new JsonArray().add(obj3);

  // not a delegate
  public static User DelegateUserFail =
      new User(
          new JsonObject()
              .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelFail = new JsonArray().add(obj3);

  // delete
  public static User successProvider =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonObject obj4 =
      new JsonObject().put("id", "36969790-b2ef-4784-936a-cac7f95eb960");
  public static JsonArray succDelReq = new JsonArray().add(obj4);

  public static String INSERT_REQ =
      "insert into test.policies "
          + "(user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values('32a4b979-4f4a-4c44-b0c3-2fe109952b5f','604cec16-0ba3-4eb9-bdcf-d2b98f1fddab','RESOURCE', "
          + " 'a13eb955-c691-4fd3-b200-f18bc78810b5','ACTIVE','2022-06-15 09:07:16.034289','{}', NOW(), NOW()) returning id";

  // create policy

  public static User consumerUser =
      new User(
          new JsonObject()
              .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
              .put("roles", new JsonArray().add(Roles.CONSUMER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static User AdminUser =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.ADMIN))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static User ProviderUserCreate =
      new User(
          new JsonObject()
              .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static JsonObject constraints = new JsonObject();

  public static JsonObject emptyCreateObject =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put("resId", "")
          .put("resType", "")
          .put("expiry_time", "")
          .put("constraints", constraints);

  public static List<CreatePolicyRequest> roleFailureReq =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(emptyCreateObject));

  //createDelegations
  public  static User createDelUser =
        new User(
          new JsonObject()
              .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
          .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static JsonObject invalidUser =    new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c33")
          .put( "resSerId", "rs.iudx.io");
  public static List<CreateDelegationRequest> userFailure =
    CreateDelegationRequest.jsonArrayToList(new JsonArray().add(invalidUser));


  public static JsonObject invalidServer =    new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put( "resSerId", "rs.iudx.org");
  public static List<CreateDelegationRequest> serverFailure =
          CreateDelegationRequest.jsonArrayToList(new JsonArray().add(invalidServer));

  public static JsonObject duplicateFail =    new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put( "resSerId", "authdev.iudx.io");
  public static List<CreateDelegationRequest> duplicateFailure =
          CreateDelegationRequest.jsonArrayToList(new JsonArray().add(duplicateFail));

}
