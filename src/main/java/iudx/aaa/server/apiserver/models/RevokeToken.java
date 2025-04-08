package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/** Vert.x data object for the revoke token API. */
@DataObject(generateConverter = true)
public class RevokeToken {

  private String rsUrl;

  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    RevokeTokenConverter.toJson(this, request);
    return request;
  }

  public RevokeToken(JsonObject request) {
    RevokeTokenConverter.fromJson(request, this);
  }

  public RevokeToken() {}

  public String getRsUrl() {
    return rsUrl;
  }

  public void setRsUrl(String rsUrl) {
    this.rsUrl = rsUrl.toLowerCase();
  }
}
