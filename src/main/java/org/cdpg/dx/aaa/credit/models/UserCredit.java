package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.*;

public record UserCredit(Optional<UUID> id, UUID userId, double balance, Optional<String> updatedAt) {

  public static UserCredit fromJson(JsonObject json) {
    return new UserCredit(
      Optional.ofNullable(json.getString(Constants.USER_CREDIT_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.USER_ID)),
      json.getDouble(Constants.BALANCE),
      Optional.ofNullable(json.getString(Constants.UPDATED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    id.ifPresent(value -> json.put(Constants.USER_CREDIT_ID, value.toString()));
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.BALANCE, balance);
    updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.USER_CREDIT_ID, value));
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.BALANCE, balance);
    updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
    return map;
  }
}
