package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record UpdateOrgDTO(Optional<String>description, String orgName, String documentPath,
                           Optional<String> updatedAt) {

  public Map<String, Object> toNonEmptyFieldsMap() {
    HashMap<String,Object> map = new HashMap<>();

    description.ifPresent(desc->map.put(Constants.ORG_DESCRIPTION,desc));
    if(orgName!=null && !orgName.isEmpty())
      map.put(Constants.ORG_NAME,orgName);
    if(documentPath!=null && !documentPath.isEmpty())
      map.put(Constants.DOCUMENTS_PATH,documentPath);
    updatedAt.ifPresent(update->map.put(Constants.UPDATED_AT,update));

    return map;
  }
}
