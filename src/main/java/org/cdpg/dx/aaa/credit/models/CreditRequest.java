package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.*;

public record CreditRequest(Optional<UUID> id, UUID userId, double amount, String status,
                                  Optional<String> requestedAt, Optional<String> processedAt) {

  public static CreditRequest fromJson(JsonObject json) {
    return new CreditRequest(
      Optional.ofNullable(json.getString(Constants.CREDIT_REQUEST_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.USER_ID)),
      json.getDouble(Constants.AMOUNT),
      json.getString(Constants.STATUS),
      Optional.ofNullable(json.getString(Constants.REQUESTED_AT)),
      Optional.ofNullable(json.getString(Constants.PROCESSED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    id.ifPresent(value -> json.put(Constants.CREDIT_REQUEST_ID, value.toString()));
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.AMOUNT, amount);
    json.put(Constants.STATUS, Optional.ofNullable(status)
      .filter(s -> !s.isEmpty())
      .orElse("pending"));
    requestedAt.ifPresent(value -> json.put(Constants.REQUESTED_AT, value));
    processedAt.ifPresent(value -> json.put(Constants.PROCESSED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.CREDIT_REQUEST_ID, value));
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    map.put(Constants.STATUS, status);
    requestedAt.ifPresent(value -> map.put(Constants.REQUESTED_AT, value));
    processedAt.ifPresent(value -> map.put(Constants.PROCESSED_AT, value));
    return map;
  }
}

