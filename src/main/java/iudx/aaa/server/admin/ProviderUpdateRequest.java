package iudx.aaa.server.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.RoleStatus;

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

  public ProviderUpdateRequest(JsonObject json) {
    ProviderUpdateRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    ProviderUpdateRequestConverter.toJson(this, obj);
    return obj;
  }

  public static List<ProviderUpdateRequest> jsonArrayToList(JsonArray json) {
    List<ProviderUpdateRequest> reg = new ArrayList<ProviderUpdateRequest>();
    json.forEach(obj -> reg.add(new ProviderUpdateRequest((JsonObject) obj)));
    return reg;
  }

}
