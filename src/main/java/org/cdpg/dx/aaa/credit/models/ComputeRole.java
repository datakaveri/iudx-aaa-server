package org.cdpg.dx.aaa.credit.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.*;

public record ComputeRole(
  Optional<UUID> id,
  UUID userId,
  String status,
  Optional<UUID> approvedBy,
  Optional<String> createdAt,
  Optional<String> updatedAt
) {

  public static ComputeRole fromJson(JsonObject json) {
    return new ComputeRole(
      Optional.ofNullable(json.getString(Constants.COMPUTE_ROLE_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.USER_ID)),
      json.getString(Constants.STATUS),
      Optional.ofNullable(json.getString(Constants.APPROVED_BY)).map(UUID::fromString),
      Optional.ofNullable(json.getString(Constants.CREATED_AT)),
      Optional.ofNullable(json.getString(Constants.UPDATED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    id.ifPresent(value -> json.put(Constants.COMPUTE_ROLE_ID, value));
    json.put(Constants.USER_ID, userId.toString());
    json.put(Constants.STATUS, status);
    approvedBy.ifPresent(value -> json.put(Constants.APPROVED_BY, value.toString()));
    createdAt.ifPresent(value -> json.put(Constants.CREATED_AT, value));
    updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.COMPUTE_ROLE_ID, value));
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.STATUS, status);
    approvedBy.ifPresent(value -> map.put(Constants.APPROVED_BY, value.toString()));
    createdAt.ifPresent(value -> map.put(Constants.CREATED_AT, value));
    updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
    return map;
  }
}
