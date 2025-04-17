package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.*;

public record CreditDeduction(Optional<UUID> id, UUID userId, double amount, UUID deductedBy,
                              Optional<String> deductedAt) {

  public static CreditDeduction fromJson(JsonObject json) {
    return new CreditDeduction(
      Optional.ofNullable(json.getString(Constants.CREDIT_DEDUCTION_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.USER_ID)),
      json.getDouble(Constants.AMOUNT),
      UUID.fromString(json.getString(Constants.DEDUCTED_BY)),
      Optional.ofNullable(json.getString(Constants.DEDUCTED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    id.ifPresent(value -> json.put(Constants.CREDIT_DEDUCTION_ID, value.toString()));
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.AMOUNT, amount);
    json.put(Constants.DEDUCTED_BY, deductedBy.toString());
    deductedAt.ifPresent(value -> json.put(Constants.DEDUCTED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.CREDIT_DEDUCTION_ID, value));
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    map.put(Constants.DEDUCTED_BY, deductedBy.toString());
    deductedAt.ifPresent(value -> map.put(Constants.DEDUCTED_AT, value));
    return map;
  }
}
