package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public record Organization(Optional<UUID> id, Optional<String>description, String orgName, String documentPath, Optional<String> createdAt,
                           Optional<String> updatedAt)
{
 public static Organization fromJson(JsonObject orgDetails)
 {
     return new Organization(Optional.ofNullable(orgDetails.getString(Constants.ORG_ID)).map(UUID::fromString),
             Optional.ofNullable(orgDetails.getString(Constants.ORG_DESCRIPTION)),
             orgDetails.getString(Constants.ORG_NAME),
             orgDetails.getString(Constants.DOCUMENTS_PATH),
             Optional.ofNullable(orgDetails.getString(Constants.CREATED_AT)),
             Optional.ofNullable(orgDetails.getString(Constants.UPDATED_AT))
     );

 }

 public JsonObject toJson()
 {
     return new JsonObject()
             .put(Constants.ORG_ID, id.map(UUID::toString).orElse(null))
             .put(Constants.ORG_DESCRIPTION,description.orElse(null))
             .put(Constants.ORG_NAME,orgName)
             .put(Constants.DOCUMENTS_PATH,documentPath)
             .put(Constants.CREATED_AT,createdAt.orElse(null))
             .put(Constants.UPDATED_AT,updatedAt.orElse(null));

 }

    public Map<String, Object> toNonEmptyFieldsMap() {
        Map<String, Object> map = new HashMap<>();

        // Add only non-empty fields
        id.ifPresent(id -> map.put(Constants.ORG_ID, id.toString()));
        description.ifPresent(desc -> map.put(Constants.ORG_DESCRIPTION, desc));
        if (orgName != null && !orgName.isEmpty()) map.put(Constants.ORG_NAME, orgName);
        if (documentPath != null && !documentPath.isEmpty()) map.put(Constants.DOCUMENTS_PATH, documentPath);
        createdAt.ifPresent(value -> map.put(Constants.CREATED_AT, value));
        updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));

        return map;

 }

}


