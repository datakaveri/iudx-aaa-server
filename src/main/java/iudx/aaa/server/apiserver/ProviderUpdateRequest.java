package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class ProviderUpdateRequest {
  UUID id;
  RoleStatus status;

  public String getId() {
    return id.toString();
  }

  public void setId(String id) {
    this.id = UUID.fromString(id);
  }

  public RoleStatus getStatus() {
    return status;
  }

  public void setStatus(RoleStatus status) {
    this.status = status;
  }

  public static List<ProviderUpdateRequest> jsonArrayToList(JsonArray json) {
    List<ProviderUpdateRequest> arr = new ArrayList<ProviderUpdateRequest>();
    json.forEach(obj -> {
      arr.add(new ProviderUpdateRequest(statusToUpperCase((JsonObject) obj)));
    });
    return arr;
  }

  public ProviderUpdateRequest(JsonObject json) {
    ProviderUpdateRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    ProviderUpdateRequestConverter.toJson(this, obj);
    return obj;
  }

  private static JsonObject statusToUpperCase(JsonObject json) {
    String castedStatus = json.getString("status").toUpperCase();
    json.remove("status");
    json.put("status", castedStatus);

    return json;
  }
}
