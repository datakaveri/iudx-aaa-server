package org.cdpg.dx.aaa.credit.models;

public enum Status {
  PENDING("pending"),
  REJECTED("rejected"),
  APPROVED("approved");

  private final String status;

  Status(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }

  private static Status temp;
  public static Status fromString(String statusStr) {
    for (Status status: Status.values()) {
      if (status.getStatus().equalsIgnoreCase(statusStr))
       return status;
    }

    throw new IllegalArgumentException("Invalid status: " + statusStr);

  }

}
