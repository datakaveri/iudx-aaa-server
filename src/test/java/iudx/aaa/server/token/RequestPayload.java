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
  
  /* Payload for tokenRevoke */
  public static JsonObject revokeTokenValidPayload = new JsonObject().put("clientId", "349b4b55-0251-490e-bee9-00f3a5d3e643")
      .put("rsUrl", "foobar.iudx.io");

  public static JsonObject revokeTokenInvalidClientId =new JsonObject().put("clientId", "149b4b55-0251-490e-bee9-00f3a5d3e643");
  public static JsonObject revokeTokenInvalidUrl = revokeTokenValidPayload.copy().put("rsUrl", "barfoo.iudx.io");
  
  /* Payload for TIP */
  public static JsonObject expiredTipPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiL"
      + "CJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjIzOTA1MTY2LCJuYmYiOjE2MjM5MD"
      + "Q1NjYsImlhdCI6MTYyMzkwNDU2NiwianRpIjoiYTcyODQ4MGYtOGQwZS00OTgyLTg3NWYtNmFhMzgzMjY1MzE2Iiw"
      + "iaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiLCJj"
      + "b25zdHJhaW50cyI6e319.YsRL87QZ68m-A1ALhAA2IdhM6BgxMjfeQxCRhf9Q2pajtFtuJGYCbcCd71xswXIRM4L7"
      + "WS05vznevd5JdxKkvQ");
  
  public static JsonObject randomToken = new JsonObject().put("accessToken",
      "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJFUzI1NmluT1RBIiwibmFtZSI6IkpvaG4gRG9lIn0.MEQCICRp"
      + "hRrc0GWowZgJAy0gL6At628Kw8YPE22iD-aKIi4PAiA0JWU-qFNL8I0tP0ws3Bbmg0FfVMn4_yk2lGGquAGOXA");
  
  public static User user(String str) {
    UUID userId = UUID.fromString(str);
    User.UserBuilder userBuilder = new UserBuilder().userId(userId);
    return new User(userBuilder);
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

