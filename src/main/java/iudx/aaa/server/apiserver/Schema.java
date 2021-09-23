package iudx.aaa.server.apiserver;


public enum Schema {
  INSTANCE;

  String schema;

  public void set(String schema) {
    if (this.schema == null) {
      this.schema = schema;
    }
  }

  @Override
  public String toString() {
    return schema;
  }
}
