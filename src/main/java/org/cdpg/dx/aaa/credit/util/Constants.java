package org.cdpg.dx.aaa.credit.util;

import java.util.List;

public class Constants {

  // CREDIT_REQUEST_TABLE
  public static final String CREDIT_REQUEST_TABLE="credit_requests";
  public static final String USER_ID = "user_id";
  public static final String CREDIT_REQUEST_ID = "id";
  public static final String AMOUNT = "amount";
  public static final String STATUS = "status";
  public static final String REQUESTED_AT = "requested_at";
  public static final String PROCESSED_AT = "processed_at";


  public static final List<String> ALL_CREDIT_REQUEST_FIELDS = List.of(
    USER_ID,
    AMOUNT,
    STATUS,
    REQUESTED_AT,
    PROCESSED_AT
  );


  // USER CREDIT TABLE
  public static final String USER_CREDIT_TABLE="user_credits";
  public static final String USER_CREDIT_ID = "id";
  public static final String BALANCE ="balance";
  public static final String UPDATED_AT ="updated_at";

  // CREDIT DEDUCTION TABLE
  public static final String CREDIT_DEDUCTION_TABLE="credit_deductions";
  public static final String CREDIT_DEDUCTION_ID = "id";
  public static final String DEDUCTED_BY = "deducted_by";
  public static final String DEDUCTED_AT = "deducted_at";


}
