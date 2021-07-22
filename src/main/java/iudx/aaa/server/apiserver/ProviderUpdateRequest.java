package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class ProviderUpdateRequest {
  UUID userId;
  RoleStatus status;

  public String getUserId() {
    return userId.toString();
  }

  public void setUserId(String userId) {
    this.userId = UUID.fromString(userId);
  }

  public RoleStatus getStatus() {
    return status;
  }

  public void setStatus(RoleStatus status) {
    this.status = status;
  }

  public static ProviderUpdateRequest validatedObj(JsonObject json) {
    return new ProviderUpdateRequest(validateJsonObject(json));
  }

  public static List<ProviderUpdateRequest> validatedList(JsonArray json) {
    List<ProviderUpdateRequest> arr = new ArrayList<ProviderUpdateRequest>();
    json.forEach(obj -> arr.add(ProviderUpdateRequest.validatedObj((JsonObject) obj)));
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

  private static JsonObject validateJsonObject(JsonObject json) throws IllegalArgumentException {
    if (!json.containsKey("userId") || !(json.getValue("userId") instanceof String)) {
      throw new IllegalArgumentException("'userId' is required");
    }

    String castedUserId = json.getString("userId").toLowerCase();
    if (!castedUserId
        .matches("^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$")) {
      throw new IllegalArgumentException("Invalid 'userId'");
    }

    if (!json.containsKey("status") || !(json.getValue("status") instanceof String)) {
      throw new IllegalArgumentException("'status' is required");
    }

    String castedStatus = json.getString("status").toUpperCase();

    if (!RoleStatus.exists(castedStatus) || castedStatus.equals(RoleStatus.PENDING.name())) {
      throw new IllegalArgumentException("Invalid 'status'");
    }

    json.remove("status");
    json.put("status", castedStatus);

    return json;
  }

}
