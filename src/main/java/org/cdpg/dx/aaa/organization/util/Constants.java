package org.cdpg.dx.aaa.organization.util;
import org.bouncycastle.pqc.crypto.newhope.NHSecretKeyProcessor;

import java.util.List;

public final class Constants {

  private Constants()
  {

  }
  public static final String ORGANIZATION_TABLE="organization";
  public static final String ORG_ID = "id";
  public static final String ORG_NAME = "name";
  public static final String ORG_DESCRIPTION = "description";
  public static final String DOCUMENTS_PATH = "document_path";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";



  public static final List<String> ALL_ORG_FIELDS = List.of(
    ORGANIZATION_TABLE,
    ORG_ID,
    ORG_NAME,
    ORG_DESCRIPTION,
    DOCUMENTS_PATH,
    CREATED_AT,
    UPDATED_AT
  );


  public static List<String> getAllPolicyFields() {
    return ALL_ORG_FIELDS;
  }

  public static final String ORG_JOIN_REQUEST_TABLE = "organization_join_requests";

  public static final String ORG_JOIN_ID = "id";
  public static final String ORGANIZATION_ID = "organization_id";
  public static final String USER_ID = "user_id";
  public static final String STATUS = "status";
  public static final String REQUESTED_AT = "requested_at";
  public static final String PROCESSED_AT = "processed_at";

  // Enum values for status
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_APPROVED = "approved";
  public static final String STATUS_REJECTED = "rejected";

  public static final String ORG_USER_ID="id";
  public static final String ROLE="role";

  public static final String ORG_CREATE_ID="id";
  public static final String ORG_CREATE_REQUEST_TABLE = "organization_create_requests";
  public static final String REQUESTED_BY="requested_by";

  public static final List<String> ALL_ORG_CREATE_REQUEST_FIELDS = List.of(
    ORG_CREATE_ID,
    ORG_CREATE_REQUEST_TABLE,
    REQUESTED_BY,
    ORG_NAME,
    ORG_DESCRIPTION,
    DOCUMENTS_PATH,
    STATUS,
    CREATED_AT,
    UPDATED_AT
  );

}
