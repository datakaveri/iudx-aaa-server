package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Vert.x data object for the update APD status API. */
@DataObject(generateConverter = true)
public class ApdUpdateRequest {
  UUID id;
  ApdStatus status;

  public String getId() {
    return id.toString();
  }

  public void setId(String id) {
    this.id = UUID.fromString(id);
  }

  public ApdStatus getStatus() {
    return status;
  }

  public void setStatus(ApdStatus status) {
    this.status = status;
  }

  public static List<ApdUpdateRequest> jsonArrayToList(JsonArray json) {
    List<ApdUpdateRequest> arr = new ArrayList<ApdUpdateRequest>();
    json.forEach(
        obj -> {
          arr.add(new ApdUpdateRequest(statusToUpperCase((JsonObject) obj)));
        });
    return arr;
  }

  public ApdUpdateRequest(JsonObject json) {
    ApdUpdateRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    ApdUpdateRequestConverter.toJson(this, obj);
    return obj;
  }

  private static JsonObject statusToUpperCase(JsonObject json) {
    String castedStatus = json.getString("status").toUpperCase();
    json.remove("status");
    json.put("status", castedStatus);

    return json;
  }
}
