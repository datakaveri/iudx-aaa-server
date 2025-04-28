package org.cdpg.dx.aaa.credit.models;

import org.postgresql.core.TransactionState;

public enum TransactionStatus {
  SUCCESS("success"),
  FAILURE("failure");

  private final String transactionStatus;

  TransactionStatus(String transactionStatus) {
    this.transactionStatus = transactionStatus;
  }

  public String getStatus() {
    return transactionStatus;
  }

  private static TransactionStatus temp;
  public static TransactionStatus fromString(String transactionStatusStr) {
    for (TransactionStatus transactionStatus: TransactionStatus.values()) {
      if (transactionStatus.getStatus().equalsIgnoreCase(transactionStatusStr))
        return transactionStatus;
    }

    throw new IllegalArgumentException("Invalid status: " + transactionStatusStr);

  }
}
