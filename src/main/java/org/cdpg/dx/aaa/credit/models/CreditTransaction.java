package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.*;

public record CreditTransaction(Optional<UUID> id, UUID userId, double amount, UUID transactedBy, Optional<String> transactionStatus,Optional<String> transactionType,
                              Optional<String> createdAt) {

  public static CreditTransaction fromJson(JsonObject json) {
    return new CreditTransaction(
      Optional.ofNullable(json.getString(Constants.CREDIT_TRANSACTION_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.USER_ID)),
      json.getDouble(Constants.AMOUNT),
      UUID.fromString(json.getString(Constants.TRANSACTED_BY)),
      Optional.ofNullable(json.getString(Constants.TRANSACTION_STATUS)),
      Optional.ofNullable(json.getString(Constants.TRANSACTION_TYPE)),
      Optional.ofNullable(json.getString(Constants.CREATED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    id.ifPresent(value -> json.put(Constants.CREDIT_TRANSACTION_ID, value.toString()));
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.AMOUNT, amount);
    json.put(Constants.TRANSACTED_BY, transactedBy.toString());
    transactionStatus.ifPresent(value -> json.put(Constants.TRANSACTION_STATUS, transactionStatus));
    transactionType.ifPresent(value -> json.put(Constants.TRANSACTION_TYPE, transactionType));
    createdAt.ifPresent(value -> json.put(Constants.CREATED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.CREDIT_TRANSACTION_ID, value));
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    map.put(Constants.TRANSACTED_BY, transactedBy.toString());
    transactionStatus.ifPresent(value -> map.put(Constants.TRANSACTION_STATUS, value));
    transactionType.ifPresent(value -> map.put(Constants.TRANSACTION_TYPE, value));
    createdAt.ifPresent(value -> map.put(Constants.CREATED_AT, value));
    return map;
  }
}

