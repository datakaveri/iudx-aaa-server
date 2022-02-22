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
          .put(
              "itemId",
              "datakaveri.org/f7e044eee8122b5c87dce6e7ad64f3266afa41dc/rs.iudx.io/aqm-bosch-climo")
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
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject providerUserFailure =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f87/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject NoCataloguePolicy =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/f8dc5bbb151968101ad4596819a248d3e0ea20c0/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject validProviderCat =
      new JsonObject()
          .put(
              "itemId",
              "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject validProviderVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "provider")
          .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637");

  public static JsonObject invalidCatDelegate =
      new JsonObject()
          .put("id", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
          .put(
              "cat_id",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put(" provider_id", "844e251b-574b-46e6-9247-f76f1f70a637")
          .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f39");

  public static JsonObject invalidDelegate =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject NoCatalogueProviderPolicy =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/f8dc5bbb151968101ad4596819a248d3e0ea20c0/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject validDelegateVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  // Requests for List Policy
  // List as a providerd34b1547-7281-4f66-b550-ed79f9bb0c36
  public static User validListPolicyProvider =
      new User(new JsonObject().put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5"));

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
      new JsonObject().put("id", "2b186e84-f9f0-4970-8d06-803bd35a5435");
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
              .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));

  public static User invalidProvider =
      new User(
          new JsonObject()
              .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "c5a34d2d-9553-4925-81fe-09aa08f29dea"));

  public static User invalidDelUser =
      new User(
          new JsonObject()
              .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "c5a34d2d-9553-4925-81fe-09aa08f29dea"));

  public static User validDelUser =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "e69debbb-4c49-4727-a779-e355414915c2"));

  public static JsonObject constraints = new JsonObject();

  public static JsonObject emptyCreateObject =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c99")
          .put("itemId", "")
          .put("itemType", "")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject invalidItem =
      new JsonObject()
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/file.iudx.io/surat-itms-realtime-information2")
          .put("itemType", "resource_group")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject duplicateItem =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject invalidDelItem =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject validItem =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject invalidCatItem =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-one")
          .put("itemType", "resource_group")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
          .put("expiryTime", "")
          .put("constraints", constraints);

  public static JsonObject validCatItem =
      new JsonObject()
          .put(
              "cat_id",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("owner_id", "844e251b-574b-46e6-9247-f76f1f70a637")
          .put("id", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
          .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f39");

  public static JsonObject validDelegateItem =
      new JsonObject()
          .put(
              "cat_id",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("owner_id", "844e251b-574b-46e6-9247-f76f1f70a637")
          .put("id", "a57cdc77-44a3-44b9-ba39-f339e40d3d21")
          .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f394");

  public static JsonObject invalidProviderItem =
      new JsonObject()
          .put(
              "cat_id",
              "datakaveri.org/0808eb81ea0e5773187ae06110f55915a55f5c05/rs.iudx.io/integration-test-rsg-one")
          .put("itemType", "resource_group")
          .put("owner_id", "e62c42f1-50e4-4053-9833-6080dcfb7102")
          .put("id", "45f8ccf3-f8fd-4c87-800f-05158eea3c6b")
          .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f394");

  public static List<CreatePolicyRequest> roleFailureReq =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(emptyCreateObject));

  public static List<CreatePolicyRequest> itemFailure =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidItem));

  public static List<CreatePolicyRequest> duplicate =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(duplicateItem));

  public static List<CreatePolicyRequest> unAuthDel =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidDelItem));

  public static List<CreatePolicyRequest> unAuthProvider =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidDelItem));

  public static List<CreatePolicyRequest> MultipleProvider =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(validItem).add(invalidCatItem));

  public static JsonObject UserCreateObject =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c99")
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/file.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("expiry_time", "")
          .put("constraints", constraints);

  public static List<CreatePolicyRequest> userFailureReq =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(UserCreateObject));
  // createDelegations
  public static User createDelUser =
      new User(
          new JsonObject()
              .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static JsonObject invalidUser =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c33")
          .put("resSerId", "rs.iudx.io");
  public static List<CreateDelegationRequest> userFailure =
      CreateDelegationRequest.jsonArrayToList(new JsonArray().add(invalidUser));

  public static JsonObject invalidServer =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put("resSerId", "rs.iudx.org");
  public static List<CreateDelegationRequest> serverFailure =
      CreateDelegationRequest.jsonArrayToList(new JsonArray().add(invalidServer));

  public static JsonObject duplicateFail =
      new JsonObject()
          .put("userId", "d34b1547-7281-4f66-b550-ed79f9bb0c36")
          .put("resSerId", "authdev.iudx.io");
  public static List<CreateDelegationRequest> duplicateFailure =
      CreateDelegationRequest.jsonArrayToList(new JsonArray().add(duplicateFail));
}
