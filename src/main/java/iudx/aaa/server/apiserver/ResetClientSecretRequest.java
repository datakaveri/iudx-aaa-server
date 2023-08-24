package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.registration.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vert.x data object for the resetClientSecret API in Registration service.
 */
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
