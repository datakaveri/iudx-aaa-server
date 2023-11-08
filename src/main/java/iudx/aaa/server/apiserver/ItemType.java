package iudx.aaa.server.apiserver;

/** Enum that defines valid item types. */
public enum ItemType {
  RESOURCE_SERVER("RESOURCE_SERVER"),
  RESOURCE_GROUP("RESOURCE_GROUP"),
  RESOURCE("RESOURCE"),
  COS("COS");

  private final String type;

  ItemType(String item) {
    this.type = item;
  }

  public String getItemType() {
    return type;
  }
}
