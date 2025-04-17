package org.cdpg.dx.aaa.credit.models;

import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record CreditDeductionDTO(UUID userId, double amount, UUID deductedBy) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    map.put(Constants.DEDUCTED_BY, deductedBy.toString());
    return map;
  }
}

