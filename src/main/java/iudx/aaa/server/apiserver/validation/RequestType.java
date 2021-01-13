package iudx.aaa.server.apiserver.validation;

public enum RequestType {
  TOKEN_REQUEST("token_request.json"),
  REVOKE_REQUEST("revoke_request.json"),
  REVOKE_ALL_REQUEST("revoke-all_request.json"),
  REGISTRATION("user_registration.json"),
  PROVIDER_REGISTRATION("provider_registration.json"),
  USER_ACCESS("user_access.json"),
  DELETE_ACCESS_POLICY("delete_policy.json"),
  ORGNIZATION_REGISTRATION("org_registration.json");


  private String fileName;

  public String getFileName() {
    return this.fileName;
  }

  private RequestType(String fileName) {
    this.fileName = fileName;
  }
}
