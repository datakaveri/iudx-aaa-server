package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.util.Constants;


import java.util.*;

//public record OrganizationCreateRequest(UUID id, UUID requestedBy, String name, String description, String documentPath,
//                                        String status, String createdAt, Optional<String> updatedAt) {


import io.vertx.codegen.annotations.DataObject;
import java.util.*;
import java.util.Optional;

  @DataObject(generateConverter = true)
  public class OrganizationCreateRequest {
    private final UUID id;
    private final UUID requestedBy;
    private final String name;
    private final String description;
    private final String documentPath;
    private final String status;
    private final String createdAt;
    private final Optional<String> updatedAt;

    public OrganizationCreateRequest(JsonObject json) {
      this.id = UUID.fromString(json.getString(Constants.ORG_CREATE_ID));
      this.requestedBy = UUID.fromString(json.getString(Constants.REQUESTED_BY));
      this.name = json.getString(Constants.ORG_NAME);
      this.description = json.getString(Constants.ORG_DESCRIPTION);
      this.documentPath = json.getString(Constants.DOCUMENTS_PATH);
      this.status = json.getString(Constants.STATUS);
      this.createdAt = json.getString(Constants.CREATED_AT);
      this.updatedAt = Optional.ofNullable(json.getString(Constants.UPDATED_AT));

      OrganizationCreateRequestConverter.fromJson(json, this);
    }

    // Constructor for full initialization
    public OrganizationCreateRequest(UUID id, UUID requestedBy, String name, String description,
                                     String documentPath, String status, String createdAt, Optional<String> updatedAt) {
      this.id = id;
      this.requestedBy = requestedBy;
      this.name = name;
      this.description = description;
      this.documentPath = documentPath;
      this.status = status;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }



    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      OrganizationCreateRequestConverter.toJson(this, json);
      return json;
    }

    public Map<String, Object> toNonEmptyFieldsMap() {
      Map<String, Object> map = new HashMap<>();
      map.put(Constants.ORG_CREATE_ID, id.toString());
      map.put(Constants.REQUESTED_BY, requestedBy.toString());
      map.put(Constants.ORG_NAME, name);
      map.put(Constants.ORG_DESCRIPTION, description);
      map.put(Constants.DOCUMENTS_PATH, documentPath);
      map.put(Constants.STATUS, status);
      map.put(Constants.CREATED_AT, createdAt);
      updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
      return map;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof OrganizationCreateRequest that)) return false;
      return Objects.equals(id, that.id) && Objects.equals(requestedBy, that.requestedBy) &&
        Objects.equals(name, that.name) && Objects.equals(description, that.description) &&
        Objects.equals(documentPath, that.documentPath) && Objects.equals(status, that.status) &&
        Objects.equals(createdAt, that.createdAt) && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, requestedBy, name, description, documentPath, status, createdAt, updatedAt);
    }

    public UUID getRequestedBy() {
      return requestedBy;
    }

    public String getName() {
      return name;
    }


    public String getDescription() {
      return description;
    }

    public String getDocumentPath() {
      return documentPath;
    }

    public String getStatus() {
      return status;
    }
  }
