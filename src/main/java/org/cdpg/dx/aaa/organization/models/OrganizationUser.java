package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;
import org.cdpg.dx.aaa.organization.util.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record OrganizationUser(UUID id, UUID organizationId, UUID userId, Role role, String createdAt, Optional<String> updatedAt) {
    public static OrganizationUser fromJson(JsonObject json) {
        return new OrganizationUser(
                UUID.fromString(json.getString(Constants.ORG_USER_ID)),
                UUID.fromString(json.getString(Constants.ORGANIZATION_ID)),
                UUID.fromString(json.getString(Constants.USER_ID)),
                Role.fromString(json.getString(Constants.ROLE)),
                json.getString(Constants.CREATED_AT),
                Optional.ofNullable(json.getString(Constants.UPDATED_AT))
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put(Constants.ORG_USER_ID, id.toString())
                .put(Constants.ORGANIZATION_ID, organizationId.toString())
                .put(Constants.USER_ID, userId.toString())
                .put(Constants.ROLE, role.getRoleName())
                .put(Constants.CREATED_AT, createdAt);
        updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
        return json;
    }

    public Map<String, Object> toNonEmptyFieldsMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.ORG_USER_ID, id.toString());
        map.put(Constants.ORGANIZATION_ID, organizationId.toString());
        map.put(Constants.USER_ID, userId.toString());
        map.put(Constants.ROLE, role.getRoleName());
        map.put(Constants.CREATED_AT, createdAt);
        updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
        return map;
    }
}
