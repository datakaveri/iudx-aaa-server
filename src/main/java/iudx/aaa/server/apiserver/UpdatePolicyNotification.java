package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static iudx.aaa.server.apiserver.util.Constants.*;

@DataObject(generateConverter = true)
public class UpdatePolicyNotification {

  UUID requestId;
  RoleStatus status;
  String expiryDuration;
  JsonObject constraints;
  
  public static List<UpdatePolicyNotification> jsonArrayToList(JsonArray json) {
    List<UpdatePolicyNotification> arr = new ArrayList<UpdatePolicyNotification>();
    json.forEach(obj -> {
      arr.add(new UpdatePolicyNotification(statusToUpperCase((JsonObject) obj)));
    });
    return arr;
  }
  
  public UpdatePolicyNotification(JsonObject json) {
    UpdatePolicyNotificationConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    UpdatePolicyNotificationConverter.toJson(this, obj);
    return obj;
  }
  
  public UUID getRequestId() {
    return requestId;
  }

  public void setRequestId(UUID requestId) {
    this.requestId = requestId;
  }

  public RoleStatus getStatus() {
    return status;
  }

  public void setStatus(RoleStatus status) {
    this.status = status;
  }

  public String getExpiryDuration() {
    return expiryDuration;
  }

  public void setExpiryDuration(String expiryDuration) {
    this.expiryDuration = expiryDuration;
  }

  public JsonObject getConstraints() {
    return constraints;
  }

  public void setConstraints(JsonObject constraints) {
    this.constraints = constraints;
  }
  
  private static JsonObject statusToUpperCase(JsonObject json) {
    String castedStatus = json.getString(STATUS).toUpperCase();
    json.put(STATUS, castedStatus);

    return json;
  }
}
