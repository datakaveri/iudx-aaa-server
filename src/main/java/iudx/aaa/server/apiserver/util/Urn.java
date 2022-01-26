package iudx.aaa.server.apiserver.util;

public enum Urn {

  URN_SUCCESS("urn:dx:as:Success"), 
  URN_MISSING_INFO("urn:dx:as:MissingInformation"),
  URN_INVALID_ROLE("urn:dx:as:InvalidRole"),
  URN_INVALID_INPUT("urn:dx:as:InvalidInput"),
  URN_ALREADY_EXISTS("urn:dx:as:AlreadyExists"),
  URN_INVALID_AUTH_TOKEN("urn:dx:as:InvalidAuthenticationToken"),
  URN_MISSING_AUTH_TOKEN("urn:dx:as:MissingAuthenticationToken");

  private String text;

  Urn(String text) {
    this.text = text;
  }

  @Override
  public String toString() {
    return text;
  }
}
