package org.cdpg.dx.aaa.credit.models;

import org.cdpg.dx.aaa.credit.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record CreditTransactionDTO(UUID userId, double amount, UUID transactedBy,String transactionType) {
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(Constants.USER_ID, userId.toString());
    map.put(Constants.AMOUNT, amount);
    map.put(Constants.TRANSACTED_BY, transactedBy.toString());
    map.put(Constants.TRANSACTION_TYPE,transactionType);
    return map;
  }
}

