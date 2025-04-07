package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record OrganizationJoinRequest(UUID id, UUID organizationId, UUID userId , String status ,String requestedAt,
                                      Optional<String> processedAt) {
  public static OrganizationJoinRequest fromJson(JsonObject orgJoinRequest) {
    return new OrganizationJoinRequest(
      UUID.fromString(orgJoinRequest.getString(Constants.ORG_JOIN_ID)),
      UUID.fromString(orgJoinRequest.getString(Constants.ORGANIZATION_ID)),
      UUID.fromString(orgJoinRequest.getString(Constants.USER_ID)),
      orgJoinRequest.getString(Constants.STATUS),
      orgJoinRequest.getString(Constants.REQUESTED_AT),
      Optional.ofNullable(orgJoinRequest.getString(Constants.PROCESSED_AT))
    );
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(Constants.ORG_JOIN_ID, id.toString())
      .put(Constants.ORGANIZATION_ID, organizationId.toString())
      .put(Constants.USER_ID, userId.toString())
      .put(Constants.STATUS, status)
      .put(Constants.REQUESTED_AT, requestedAt)
      .put(Constants.PROCESSED_AT, processedAt.orElse(null));
  }

  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    map.put(Constants.ORG_JOIN_ID, id.toString());
    map.put(Constants.ORGANIZATION_ID, organizationId.toString());
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.STATUS, status);
    map.put(Constants.REQUESTED_AT, requestedAt);
    processedAt.ifPresent(value -> map.put(Constants.PROCESSED_AT, value));

    return map;
  }
}
