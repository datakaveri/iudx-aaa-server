package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;


import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public record OrganizationCreateRequest(Optional<UUID> id, UUID requestedBy, String name, String description, String documentPath,
                                        String status, Optional<String> createdAt, Optional<String> updatedAt) {
  public static OrganizationCreateRequest fromJson(JsonObject json) {
    return new OrganizationCreateRequest(
      Optional.ofNullable(json.getString(Constants.ORG_JOIN_ID)).map(UUID::fromString),
      UUID.fromString(json.getString(Constants.REQUESTED_BY)),
      json.getString(Constants.ORG_NAME),
      json.getString(Constants.ORG_DESCRIPTION),
      json.getString(Constants.DOCUMENTS_PATH),
      json.getString(Constants.STATUS),
      Optional.ofNullable(json.getString(Constants.CREATED_AT)),
      Optional.ofNullable(json.getString(Constants.UPDATED_AT))
    );
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
      id.ifPresent(value -> json.put(Constants.ORG_CREATE_ID,value));
      json.put(Constants.REQUESTED_BY, requestedBy.toString())
      .put(Constants.ORG_NAME, name)
      .put(Constants.ORG_DESCRIPTION, description)
      .put(Constants.DOCUMENTS_PATH, documentPath)
      .put(Constants.STATUS, Optional.ofNullable(status)
        .filter(s -> !s.isEmpty())
        .orElse(Status.PENDING.getStatus()));
    createdAt.ifPresent(value-> json.put(Constants.CREATED_AT,value));
    updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
    return json;
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    id.ifPresent(value -> map.put(Constants.ORG_CREATE_ID, value));
    map.put(Constants.REQUESTED_BY, requestedBy.toString());
    map.put(Constants.ORG_NAME, name);
    map.put(Constants.ORG_DESCRIPTION, description);
    map.put(Constants.DOCUMENTS_PATH, documentPath);
    map.put(Constants.STATUS, status);
    createdAt.ifPresent(value -> map.put(Constants.CREATED_AT, value));
    updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
    return map;
  }

}

