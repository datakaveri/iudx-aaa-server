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
          .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject roleFailure2 =
          new JsonObject()
                  .put(
                          "itemId",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "RESOURCE_SERVER")
                  .put("role", "consumer")
                  .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");
  public static JsonObject roleFailure3 =
          new JsonObject()
                  .put(
                          "itemId",
                          "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "ADMIN")
                  .put("role", "ADMIN")
                  .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject invalidItemId =
      new JsonObject()
          .put("itemId", "rs.iudx.io")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject invalidItemId2 =
          new JsonObject()
                  .put("itemId", "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg/rs.iudx.io/testing-insert-rsg/rs.iudx.io")
                  .put("itemType", "RESOURCE_GROUP")
                  .put("role", "consumer")
                  .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject invalidItemId3 =
          new JsonObject()
                  .put("itemId", "iisc.ac.in/89a36273d7/rs.iudx.io/testing-insert-rsg")
                  .put("itemType", "RESOURCE")
                  .put("role", "consumer")
                  .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject consumerVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "e69debbb-4c49-4727-a779-e355414915c2");

  public static JsonObject apdResourceGrpPolicy =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/sub-sample")
          .put("itemType", "resource_group")
          .put("role", "consumer")
          .put("userId", "e69debbb-4c49-4727-a779-e355414915c2");

  public static JsonObject NoCataloguePolicy =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "e69debbb-4c49-4727-a779-e355414915c2");

  public static JsonObject validProviderCat =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "provider")
          .put("userId", "b2c27f3f-2524-4a84-816e-91f9ab23f837");

  public static JsonObject validProviderVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information")
          .put("itemType", "resource_group")
          .put("role", "provider")
          .put("userId", "b2c27f3f-2524-4a84-816e-91f9ab23f837");

  public static JsonObject invalidDelegate =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "e69debbb-4c49-4727-a779-e355414915c2");

  public static JsonObject NoCatalogueProviderPolicy =
      new JsonObject()
          .put(
              "itemId",
              "datakaveri.org/f8dc5bbb151968101ad4596819a248d3e0ea20c0/cat-test.iudx.io/catalogue/crud")
          .put("itemType", "catalogue")
          .put("role", "delegate")
          .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  public static JsonObject validDelegateVerification =
      new JsonObject()
          .put(
              "itemId",
              "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg")
          .put("itemType", "resource_group")
          .put("role", "delegate")
          .put("userId", "c5a34d2d-9553-4925-81fe-09aa08f29dea");

  // Requests for List Policy
  public static User validListPolicyProvider =
      new User(new JsonObject().put("userId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));

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
              .put("userId", "b2c27f3f-2524-4a84-816e-91f9ab23f837")
              .put("roles", new JsonArray().add(Roles.ADMIN)));
  public static User allRolesUser2 =
          new User(
                  new JsonObject()
                          .put("userId", "00000000-0000-0000-0000-000000000000")
                          .put("roles", new JsonArray().add(Roles.ADMIN)));

  public static User allRolesUser3 =
          new User(
                  new JsonObject()
                          .put("userId", "844e251b-574c-46e6-9247-f76f1f70a637")
                          .put("roles", new JsonArray().add(Roles.CONSUMER)));
  public static JsonObject obj1 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonObject obj2 = new JsonObject().put("id", UUID.randomUUID());
  public static JsonArray ResExistFail = new JsonArray().add(obj1).add(obj2);

  // res not owned by user
  public static User ProviderUser =
    new User(
              new JsonObject()
                      .put("userId", "e69debbb-4c49-4727-a779-e355414915c2")
                      .put("roles", new JsonArray().add(Roles.PROVIDER))
                      .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));

  public static JsonObject  ResOwnFailID=
          new JsonObject().put("id", "b44c36b6-cffd-453b-87f3-b17b957d6128");
  public static JsonArray ResOwnFail = new JsonArray().add(ResOwnFailID);

  // no policy for delegate
  public static User DelegateUser =
      new User(
          new JsonObject()
                  .put("userId", "e69debbb-4c49-4727-a779-e355414915c2")
                  .put("roles", new JsonArray().add(Roles.DELEGATE))
                  .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelPolFail = new JsonArray().add(ResOwnFailID);

  // not a delegate
  public static User DelegateUserFail =
      new User(
          new JsonObject()
              .put("userId", "e69debbb-4c49-4727-a779-e355414915c2")
              .put("roles", new JsonArray().add(Roles.DELEGATE))
              .put("keycloakId", "04617f23-7e5d-4118-8773-1b6c85da14ed"));
  public static JsonArray DelFail = new JsonArray().add(ResOwnFailID);

  // delete
  public static User successProvider =
      new User(
              new JsonObject()
                      .put("userId", "b2c27f3f-2524-4a84-816e-91f9ab23f837")
                      .put("roles", new JsonArray().add(Roles.PROVIDER))
                      .put("keycloakId", "b2c27f3f-2524-4a84-816e-91f9ab23f837"));


  public static String INSERT_USER_POL =
      "insert into policies "
          + "(id, user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, 'c5a34d2d-9553-4925-81fe-09aa08f29dea','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " 'b2c27f3f-2524-4a84-816e-91f9ab23f837','ACTIVE','2023-06-15 09:07:16.034289','{}', NOW(), NOW())";

  public static String INSERT_EXPIRED_USER_POL =
      "insert into policies "
          + "(id, user_id,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, 'c5a34d2d-9553-4925-81fe-09aa08f29dea','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " 'b2c27f3f-2524-4a84-816e-91f9ab23f837','ACTIVE','1998-06-15 09:07:16.034289','{}', NOW(), NOW())";

  public static String INSERT_APD_POL =
      "insert into apd_policies "
          + "(id, apd_id, user_class,item_id,item_type,owner_id,status,expiry_time,constraints,created_at,updated_at) "
          + "values($1::uuid, '88b7bdbc-936c-4478-8059-f95f4c8a6352','unitTest','66ff82fb-1720-415d-a2f3-18ff65c0e050','RESOURCE', "
          + " 'b2c27f3f-2524-4a84-816e-91f9ab23f837','ACTIVE','2023-06-15 09:07:16.034289','{}', NOW(), NOW())";
}
