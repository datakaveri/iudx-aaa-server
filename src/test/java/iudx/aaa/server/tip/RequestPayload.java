package iudx.aaa.server.tip;

import io.vertx.core.json.JsonObject;

/**
 * Structures the JsonObject required for test cases.
 */
public class RequestPayload {
  
  public static JsonObject validPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiLC"
      + "JhbGciOiJFUzI1NiJ9.eyJzdWIiOiIwMzQzZGFiNi1hYTYxLTQwMjQtYTZmZi0zYjUyZThkNDg4ZjQiLCJpc3MiOi"
      + "JhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjg2OTM2NTEyLCJuYmYiOjE2MjM4Nj"
      + "M5MTIsImlhdCI6MTYyMzg2MzkxMiwianRpIjoiM2FiM2M1NjgtNzZmNC00ZWM0LTg5ZWMtYmNhYWY4NWM1MzNhIi"
      + "wiaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoicHJvdmlkZXIiLC"
      + "Jjb25zdHJhaW50cyI6e319.bbyZsjvmfa30F5Q4AT_fetS1l1klglnczn43kKsdR8VT__Ir1wsiKrMuLM3QSPpxc"
      + "7PaxeypNXou_B9x7nNi8A");
  
  public static JsonObject expiredPayload = new JsonObject().put("accessToken", "eyJ0eXAiOiJKV1QiL"
      + "CJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIwMzQzZGFiNi1hYTYxLTQwMjQtYTZmZi0zYjUyZThkNDg4ZjQiLCJpc3MiO"
      + "iJhdXRoLnRlc3QuY29tIiwiYXVkIjoidGVzdC5jYXQuY29tIiwiZXhwIjoxNjIzNzgzNTQ4LCJuYmYiOjE2MjM3OD"
      + "I5NDgsImlhdCI6MTYyMzc4Mjk0OCwianRpIjoiYzJhNjFiMGYtNGFhMi00ZGI0LTk4ZTItODNjNTJkOTJhYWQyIiw"
      + "iaXRlbV9pZCI6Iml0ZW0tMSIsIml0ZW1fdHlwZSI6IlJlc291cmNlR3JvdXAiLCJyb2xlIjoiYWRtaW4iLCJjb25z"
      + "dHJhaW50cyI6e319.YZVzQyFFpVlk_VvcS1dNp4dgLhy9y1RyaARqSQpfviPH2oD2-fm_u02L2AqYzk0Se0AG2Ht1"
      + "wVb362zkv5NDSw");
  
  public static JsonObject invalidPayload = new JsonObject().put("accessToken", validPayload.getString("accessToken")+"abc");
}

