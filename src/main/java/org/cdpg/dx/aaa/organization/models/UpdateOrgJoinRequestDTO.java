package org.cdpg.dx.aaa.organization.models;

import org.cdpg.dx.aaa.organization.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record UpdateOrgJoinRequestDTO(String status, Optional<String> processedAt) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    if (status != null && !status.isEmpty()) {
      map.put(Constants.STATUS, status);
    }
    processedAt.ifPresent(val -> map.put(Constants.PROCESSED_AT, val));
    return map;
  }
}
