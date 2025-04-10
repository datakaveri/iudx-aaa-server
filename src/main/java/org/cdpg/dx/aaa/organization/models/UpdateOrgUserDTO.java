package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgUserDTO(Role role,Optional<String> updatedAt) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    HashMap<String,Object> map = new HashMap<>();

    if(role.getRoleName()!=null & !role.getRoleName().isEmpty())
      map.put(Constants.ROLE,role.getRoleName());
    updatedAt.ifPresent(update->map.put(Constants.UPDATED_AT,update));

    return map;
  }
}
