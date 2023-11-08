package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/** Vert.x data object for the introspect token API. */
@DataObject(generateConverter = true)
public class IntrospectToken {

  private String accessToken;

  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    IntrospectTokenConverter.toJson(this, request);
    return request;
  }

  public IntrospectToken(JsonObject request) {
    IntrospectTokenConverter.fromJson(request, this);
  }

  public IntrospectToken() {}

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
