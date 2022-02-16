package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class ApdUpdateRequest {
  UUID apdId;
  ApdStatus status;

  public String getApdId() {
    return apdId.toString();
  }

  public void setApdId(String apdId) {
    this.apdId = UUID.fromString(apdId);
  }

  public ApdStatus getStatus() {
    return status;
  }

  public void setStatus(ApdStatus status) {
    this.status = status;
  }

  public static List<ApdUpdateRequest> jsonArrayToList(JsonArray json) {
    List<ApdUpdateRequest> arr = new ArrayList<ApdUpdateRequest>();
    json.forEach(obj -> {
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
