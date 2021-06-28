package iudx.aaa.server.token;

import java.util.Arrays;
import java.util.UUID;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RevokeToken;
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
      .put("role", "provider");
  
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
      + "JhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjg3MDIwNTgxLCJuYmYiOjE2MjM5M"
      + "DYwNzcsImlhdCI6MTYyMzkwNjA3NywianRpIjoiYmJjZGVlMjktNmE4Yy00NTdkLTliNzEtMDA3OGI2ZGJiM2Y5I"
      + "iwiaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiL"
      + "CJjb25zdHJhaW50cyI6e319.UROV2_XdHXTpnWERtdXrfm0sraNClm70j17Gf_IOHaRDBNIs5RGTz9hKs9qZAPKrv"
      + "RCmFg7DUGkAdsFWdKY8AA");
  
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
  
  public static RequestToken mapToReqToken(JsonObject request) {
    return request.copy().mapTo(RequestToken.class);
  }
  
  public static RevokeToken mapToRevToken(JsonObject request) {
    return request.copy().mapTo(RevokeToken.class);
  }
  
  public static IntrospectToken mapToInspctToken(JsonObject request) {
    return request.copy().mapTo(IntrospectToken.class);
  }
  
}

