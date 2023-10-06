package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

/** Vert.x data object for the resetClientSecret API in Registration service. */
@DataObject(generateConverter = true)
public class ResetClientSecretRequest {

  UUID clientId;

  public ResetClientSecretRequest(JsonObject json) {
    ResetClientSecretRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    ResetClientSecretRequestConverter.toJson(this, obj);
    return obj;
  }

  public String getClientId() {
    return this.clientId.toString();
  }

  public void setClientId(String orgId) {
    this.clientId = UUID.fromString(orgId);
  }
}
