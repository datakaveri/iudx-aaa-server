package iudx.aaa.server.token;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;

/**
 * Structures the JsonObject required for test cases.
 */
public class RequestPayload {
  
  /* Payload for createToken */
  public static JsonArray roleList = new JsonArray("[\"delegate\",\"provider\"]");
  
  public static JsonObject validPayload = new JsonObject().put("clientId", "349b4b55-0251-490e-bee9-00f3a5d3e643")
      .put("clientSecret","48434da1-411d-42d6-894a-557fd5b9965e")
      .put("itemId", "item-1")
      .put("itemType", "ResourceGroup")
      .put("role", "consumer");
  
  public static JsonObject invalidClientId = validPayload.copy().put("clientId", "0343dab6-aa61-4024-a6ff-3b52e8d488f1");
  
  public static JsonObject invalidClientSecret = validPayload.copy().put("clientSecret", "0343dab6-aa61-4024-a6ff-3b52e8d488f1");
  
  public static JsonObject undefinedRole = validPayload.copy().put("role", "dev");
  
  /* Payload for tokenRevoke */
  public static JsonObject revokeTokenValidPayload = new JsonObject().put("clientId", "349b4b55-0251-490e-bee9-00f3a5d3e643")
      .put("rsUrl", "foobar.iudx.io");

  public static JsonObject revokeTokenInvalidClientId =new JsonObject().put("clientId", "149b4b55-0251-490e-bee9-00f3a5d3e643");
  public static JsonObject revokeTokenInvalidUrl = revokeTokenValidPayload.copy().put("rsUrl", "barfoo.iudx.io");
  
  /* Payload for TIP */
  public static JsonObject validTipPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiLC"
      + "JhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiOiJh"
      + "dXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjg4NjA5MzMyLCJpYXQiOjE2MjU0OTQxMzI"
      + "sImlpZCI6InJpOmRhdGFrYXZlcmkub3JnL2Y3ZTA0NGVlZTgxMjJiNWM4N2RjZTZlN2FkNjRmMzI2NmFmYTQxZGMvcn"
      + "MuaXVkeC5pby9hcW0tYm9zY2gtY2xpbW8vRldSMDE3Iiwicm9sZSI6InByb3ZpZGVyIiwiY29ucyI6e319.CHivWv6v"
      + "Oz8FH5jiTfaTq8rQnyG1Pd4pusEj6PBWp4ouRlKHQcsuo50ZLMHvPBv2YQADOXS_XlZfg4OsDAsffg");
  
  public static JsonObject expiredTipPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiL"
      + "CJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjIzOTA1MTY2LCJuYmYiOjE2MjM5MD"
      + "Q1NjYsImlhdCI6MTYyMzkwNDU2NiwianRpIjoiYTcyODQ4MGYtOGQwZS00OTgyLTg3NWYtNmFhMzgzMjY1MzE2Iiw"
      + "iaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiLCJj"
      + "b25zdHJhaW50cyI6e319.YsRL87QZ68m-A1ALhAA2IdhM6BgxMjfeQxCRhf9Q2pajtFtuJGYCbcCd71xswXIRM4L7"
      + "WS05vznevd5JdxKkvQ");
  
  public static JsonObject invalidTipPayload = new JsonObject().put("accessToken", validTipPayload.getString("accessToken")+"abc");
  
  
  public static User user(String str) {
    UUID userId = UUID.fromString(str);
    User.UserBuilder userBuilder = new UserBuilder().userId(userId);
    return new User(userBuilder);
  }
  
  public static User clientFlowUser() {
    UUID kid = UUID.fromString("7539b9a4-9484-4efb-9a61-4c6a971df04d");
    UUID userId = UUID.fromString("32a4b979-4f4a-4c44-b0c3-2fe109952b5f");
    List<Roles> roles = RequestPayload.processRoles(new JsonArray().add("CONSUMER"));
    
    User.UserBuilder userBuilder = new UserBuilder().userId(userId).keycloakId(kid).roles(roles);
    return new User(userBuilder);
  }
  
  public static User oidcFlowUser() {
    UUID kid = UUID.fromString("c46e7a5d-7c2d-471e-8222-6a59a5095e7a");
    UUID userId = UUID.fromString("a13eb955-c691-4fd3-b200-f18bc78810b5");
    List<Roles> roles = RequestPayload.processRoles(new JsonArray("[\"delegate\",\"provider\"]"));

    User.UserBuilder userBuilder =
        new UserBuilder().userId(userId).keycloakId(kid).roles(roles).name("good", "bye");
    return new User(userBuilder);
  }
  
  public static RequestToken mapToReqToken(JsonObject request) {
    return request.copy().mapTo(RequestToken.class);
  }
  
  public static RevokeToken mapToRevToken(JsonObject request) {
    return request.copy().mapTo(RevokeToken.class);
  }
  
  public static IntrospectToken mapToInspctToken(JsonObject request) {
    return request.copy().mapTo(IntrospectToken.class);
  }
  
  public static List<Roles> processRoles(JsonArray role) {
    List<Roles> roles = role.stream()
        .filter(a -> Roles.exists(a.toString()))
        .map(a -> Roles.valueOf(a.toString()))
        .collect(Collectors.toList());
    
    return roles;
  }
  
}

