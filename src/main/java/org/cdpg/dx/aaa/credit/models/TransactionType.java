package org.cdpg.dx.aaa.credit.models;

public enum TransactionType {
  CREDIT("credit"),
  DEBIT("debit");

  private final String transactionType;

  TransactionType(String transactionType) {
    this.transactionType = transactionType;
  }

  public String getType() {
    return transactionType;
  }

  private static TransactionType temp;
  public static TransactionType fromString(String transactionTypeStr) {
    for (TransactionType transactionType: TransactionType.values()) {
      if (transactionType.getType().equalsIgnoreCase(transactionTypeStr))
        return transactionType;
    }

    throw new IllegalArgumentException("Invalid status: " + transactionTypeStr);

  }
}
