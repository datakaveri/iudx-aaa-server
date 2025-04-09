package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgCreateRequestDTO(String status, String orgName, Optional<String> description, Optional<String> documentPath, Optional<String> updatedAt) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (status != null && !status.isEmpty()) {
      map.put(Constants.STATUS, status);
    }
    if(orgName!=null && !orgName.isEmpty())
      map.put(Constants.ORG_NAME,orgName);
    description.ifPresent(value -> map.put(Constants.ORG_DESCRIPTION, value));
    documentPath.ifPresent(value -> map.put(Constants.DOCUMENTS_PATH, value));
    updatedAt.ifPresent(value -> map.put(Constants.UPDATED_AT, value));

    return map;
  }
}
