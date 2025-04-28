package org.cdpg.dx.aaa.credit.models;

import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record CreditRequestDTO(UUID userId, double amount) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    return map;
  }
}
