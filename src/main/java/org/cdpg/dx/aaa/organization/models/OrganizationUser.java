package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record OrganizationUser(Optional<UUID> id, UUID organizationId, UUID userId, Role role, Optional<String> createdAt, Optional<String> updatedAt) {
    public static OrganizationUser fromJson(JsonObject json) {
        return new OrganizationUser(
          Optional.ofNullable(json.getString(Constants.ORG_USER_ID)).map(UUID::fromString),
          UUID.fromString(json.getString(Constants.ORGANIZATION_ID)),
          UUID.fromString(json.getString(Constants.USER_ID)),
          Role.fromString(json.getString(Constants.ROLE)),
          Optional.ofNullable(json.getString(Constants.CREATED_AT)),
          Optional.ofNullable(json.getString(Constants.UPDATED_AT))
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        id.ifPresent(value -> json.put(Constants.ORG_USER_ID,value));
        json.put(Constants.ORGANIZATION_ID, organizationId.toString())
                .put(Constants.USER_ID, userId.toString())
                .put(Constants.ROLE, role.getRoleName());
        createdAt.ifPresent(value-> json.put(Constants.CREATED_AT,value));
        updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
        return json;
    }

    public Map<String, Object> toNonEmptyFieldsMap() {
        Map<String, Object> map = new HashMap<>();
        id.ifPresent(value -> map.put(Constants.ORG_USER_ID, value));
        map.put(Constants.ORGANIZATION_ID, organizationId.toString());
        map.put(Constants.USER_ID, userId.toString());
        map.put(Constants.ROLE, role.getRoleName());
        createdAt.ifPresent(value -> map.put(Constants.CREATED_AT, value));
        updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
        return map;
    }
}
