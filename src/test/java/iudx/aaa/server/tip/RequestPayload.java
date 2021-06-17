package iudx.aaa.server.tip;

import io.vertx.core.json.JsonObject;

/**
 * Structures the JsonObject required for test cases.
 */
public class RequestPayload {
  
  public static JsonObject validPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiLC"
      + "JhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjg3MDIwNTgxLCJuYmYiOjE2MjM5M"
      + "DYwNzcsImlhdCI6MTYyMzkwNjA3NywianRpIjoiYmJjZGVlMjktNmE4Yy00NTdkLTliNzEtMDA3OGI2ZGJiM2Y5I"
      + "iwiaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiL"
      + "CJjb25zdHJhaW50cyI6e319.UROV2_XdHXTpnWERtdXrfm0sraNClm70j17Gf_IOHaRDBNIs5RGTz9hKs9qZAPKrv"
      + "RCmFg7DUGkAdsFWdKY8AA");
  
  public static JsonObject expiredPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiL"
      + "CJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzNDliNGI1NS0wMjUxLTQ5MGUtYmVlOS0wMGYzYTVkM2U2NDMiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjIzOTA1MTY2LCJuYmYiOjE2MjM5MD"
      + "Q1NjYsImlhdCI6MTYyMzkwNDU2NiwianRpIjoiYTcyODQ4MGYtOGQwZS00OTgyLTg3NWYtNmFhMzgzMjY1MzE2Iiw"
      + "iaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiLCJj"
      + "b25zdHJhaW50cyI6e319.YsRL87QZ68m-A1ALhAA2IdhM6BgxMjfeQxCRhf9Q2pajtFtuJGYCbcCd71xswXIRM4L7"
      + "WS05vznevd5JdxKkvQ");
  
  public static JsonObject invalidPayload = new JsonObject().put("accessToken", validPayload.getString("accessToken")+"abc");
}

