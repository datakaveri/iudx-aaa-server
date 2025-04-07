package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

//public record OrgCreateRequestDTO(String status, String orgName, Optional<String> description, Optional<String> documentPath, Optional<String> updatedAt) {

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

  @DataObject
  @JsonGen(publicConverter = false)
  public class UpdateOrgCreateRequestDTO {
    private final String status;
    private final String orgName;
    private final Optional<String> description;
    private final Optional<String> documentPath;
    private final Optional<String> updatedAt;

    public UpdateOrgCreateRequestDTO(JsonObject json) {
      this.status = json.getString(Constants.STATUS);
      this.orgName = json.getString(Constants.ORG_NAME);
      this.description = Optional.ofNullable(json.getString(Constants.ORG_DESCRIPTION));
      this.documentPath = Optional.ofNullable(json.getString(Constants.DOCUMENTS_PATH));
      this.updatedAt = Optional.ofNullable(json.getString(Constants.UPDATED_AT));
    }

    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.put(Constants.STATUS, status);
      json.put(Constants.ORG_NAME, orgName);
      description.ifPresent(value -> json.put(Constants.ORG_DESCRIPTION, value));
      documentPath.ifPresent(value -> json.put(Constants.DOCUMENTS_PATH, value));
      updatedAt.ifPresent(value -> json.put(Constants.UPDATED_AT, value));
      return json;
    }

    public Map<String, Object> toNonEmptyFieldsMap() {
      Map<String, Object> map = new HashMap<>();
      if (status != null && !status.isEmpty()) {
        map.put(Constants.STATUS, status);
      }
      if (orgName != null && !orgName.isEmpty()) {
        map.put(Constants.ORG_NAME, orgName);
      }
      description.ifPresent(value -> map.put(Constants.ORG_DESCRIPTION, value));
      documentPath.ifPresent(value -> map.put(Constants.DOCUMENTS_PATH, value));
      updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));
      return map;
    }
  }
