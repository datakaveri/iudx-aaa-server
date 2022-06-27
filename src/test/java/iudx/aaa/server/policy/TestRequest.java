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

  public static final String AUTH_SERVER_URL = "authvertx.iudx.io";
  // Requests for verify policy

  public static JsonObject roleFailure =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject invalidItemId =
      new JsonObject()
          .put("itemId", "rs.iudx.io")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  public static JsonObject consumerVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject apdResourceGrpPolicy =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/sub-sample")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject NoCataloguePolicy =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

  public static JsonObject validProviderCat =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637");

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
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5");

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
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683");

  // Requests for List Policy
  public static User validListPolicyProvider =
      new User(new JsonObject().put("userId", "844e251b-574b-46e6-9247-f76f1f70a637"));

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
              .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637")
              .put("roles", new JsonArray().add(Roles.ADMIN)));
  public static JsonObject obj1 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonObject obj2 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonArray ResExistFail = new JsonArray().add(obj1).add(obj2);

  // res not owned by user
  public static User ProviderUser =
    new User(
              new JsonObject()
                      .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                      .put("roles", new JsonArray().add(Roles.PROVIDER))
                      .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static JsonObject  ResOwnFailID=
          new JsonObject().put("id", "b44c36b6-cffd-453b-87f3-b17b957d6128");
  public static JsonArray ResOwnFail = new JsonArray().add(ResOwnFailID);

  // no policy for delegate
  public static User DelegateUser =
      new User(
          new JsonObject()
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("roles", new JsonArray().add(Roles.DELEGATE))
                  .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelPolFail = new JsonArray().add(ResOwnFailID);

  // not a delegate
  public static User DelegateUserFail =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelFail = new JsonArray().add(ResOwnFailID);

  // delete
  public static User successProvider =
      new User(
              new JsonObject()
                      .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637")
                      .put("roles", new JsonArray().add(Roles.PROVIDER))
                      .put("keycloakId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));
  public static JsonObject obj4 =
      new JsonObject().put("id", "36969790-b2ef-4784-936a-cac7f95eb960");
  public static JsonArray succDelReq = new JsonArray().add(obj4);


  public static String INSERT_USER_POL =
      "insert into policies "
          + "(id, user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, 'd1262b13-1cbe-4b66-a9b2-96df86437683','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " '844e251b-574b-46e6-9247-f76f1f70a637','ACTIVE','2023-06-15 09:07:16.034289','{}', NOW(), NOW())";

  public static String INSERT_EXPIRED_USER_POL =
      "insert into policies "
          + "(id, user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, 'd1262b13-1cbe-4b66-a9b2-96df86437683','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " '844e251b-574b-46e6-9247-f76f1f70a637','ACTIVE','1998-06-15 09:07:16.034289','{}', NOW(), NOW())";

  public static String INSERT_APD_POL =
      "insert into apd_policies "
          + "(id, apd_id, user_class,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, '88b7bdbc-936c-4478-8059-f95f4c8a6352','unitTest','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " '844e251b-574b-46e6-9247-f76f1f70a637','ACTIVE','2023-06-15 09:07:16.034289','{}', NOW(), NOW())";

  // create policy
  public static User consumerUser =
      new User(
          new JsonObject()
              .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637")
              .put("roles", new JsonArray().add(Roles.CONSUMER))
              .put("keycloakId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));

  public static User validProvider =
          new User(
                  new JsonObject()
                          .put("userId", "844e251b-574b-46e6-9247-f76f1f70a637")
                          .put("roles", new JsonArray().add(Roles.PROVIDER))
                          .put("keycloakId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));;

  public static User invalidProvider =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.PROVIDER))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static User invalidDelUser =
      new User(
          new JsonObject()
              .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static User validDelUser =
      new User(
          new JsonObject()
              .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "c5a34d2d-9553-4925-81fe-09aa08f29dea"));

  public static User validTrusteeUser =
          new User(
                  new JsonObject()
                          .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
                          .put("roles", new JsonArray().add(Roles.TRUSTEE))
                          .put("keycloakId", "c5a34d2d-9553-4925-81fe-09aa08f29dea"));
  public static JsonObject constraints = new JsonObject();

  public static JsonObject validReqItem =
          new JsonObject()
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("itemId", "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "resource_group")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject invalidReqItem =
          new JsonObject()
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("itemId", "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidCatId")
                  .put("itemType", "resource_group")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject validCatItem =
          new JsonObject()
                  .put(
                          "cat_id",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "resource_group")
                  .put("owner_id", "844e251b-574b-46e6-9247-f76f1f70a637")
                  .put("id", "a57cdc77-44a3-44b9-ba39-f339e40d3d21")
                  .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f394");

  public static JsonObject duplicateItem =
          new JsonObject()
                  .put(
                          "itemId",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "resource_group")
                  .put("userId", "d1262b13-1cbe-4b66-a9b2-96df86437683")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject invalidProviderItem =
          new JsonObject()
                  .put(
                          "cat_id",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidCatId")
                  .put("itemType", "resource_group")
                  .put("owner_id", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("id", "a57cdc77-44a3-44b9-ba39-f339e40d3d21")
                  .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f394");

  public static JsonObject invalidDelItem =
          new JsonObject()
                  .put(
                          "itemId",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidCatId")
                  .put("itemType", "resource_group")
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("expiryTime", "")
                  .put("constraints", constraints);


  public static JsonObject validDelegateItem =
      new JsonObject()
          .put(
              "cat_id",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("owner_id", "844e251b-574b-46e6-9247-f76f1f70a637")
          .put("id", "a57cdc77-44a3-44b9-ba39-f339e40d3d21")
          .put("resource_server_id", "551955e9-e450-4b23-b69e-f0b7e554f394");


  public static JsonObject invalidTrusteeItem =
          new JsonObject()
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("itemId", "authdev.iudx.io")
                  .put("itemType", "apd")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject invalidApdTrusteeItem =
          new JsonObject()
                  .put("userId", "a13eb955-c691-4fd3-b200-f18bc78810b5")
                  .put("itemId", "authdev.iudx.ip")
                  .put("itemType", "apd")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject invalidApdIdPolicyItem =
          new JsonObject()
                  .put("userClass", "testUserClass")
                  .put("apdId", "authdev.iudx.ip")
                  .put("itemId", "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "resource_group")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static JsonObject invalidApdItemIdPolicyItem =
          new JsonObject()
                  .put("userClass", "testUserClass")
                  .put("apdId", "authdev.iudx.io")
                  .put("itemId", "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidRes")
                  .put("itemType", "resource_group")
                  .put("expiryTime", "")
                  .put("constraints", constraints);

  public static List<CreatePolicyRequest> validReq =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(validReqItem));

  public static List<CreatePolicyRequest> itemFailure =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidReqItem));

  public static List<CreatePolicyRequest> duplicate =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(duplicateItem));

  public static List<CreatePolicyRequest> unAuthDel =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidDelItem));

  public static List<CreatePolicyRequest> unAuthProvider =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidDelItem));

  public static List<CreatePolicyRequest> MultipleProvider =
      CreatePolicyRequest.jsonArrayToList(new JsonArray().add(validReqItem).add(invalidReqItem));

  public static List<CreatePolicyRequest> invalidTrustee =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidTrusteeItem));

  public static List<CreatePolicyRequest> invalidApdTrustee =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidApdTrusteeItem));

  public static List<CreatePolicyRequest> invalidApdPolicyId =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidApdIdPolicyItem));

  public static List<CreatePolicyRequest> invalidApdPolicyItemType =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidApdItemIdPolicyItem));

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
          .put("resSerId", "authvertx.iudx.io");
  public static List<CreateDelegationRequest> duplicateFailure =
      CreateDelegationRequest.jsonArrayToList(new JsonArray().add(duplicateFail));
}
