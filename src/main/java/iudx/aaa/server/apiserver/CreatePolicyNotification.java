package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class CreatePolicyNotification {
  
  String itemId;
  String itemType;
  String expiryDuration;
  JsonObject constraints;
  
  
  public static List<CreatePolicyNotification> jsonArrayToList(JsonArray json) {
    List<CreatePolicyNotification> arr = new ArrayList<CreatePolicyNotification>();
    json.forEach(obj -> {
      arr.add(new CreatePolicyNotification((JsonObject) obj));
    });
    return arr;
  }
  
  public CreatePolicyNotification(JsonObject json) {
    CreatePolicyNotificationConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreatePolicyNotificationConverter.toJson(this, obj);
    return obj;
  }
  
  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public String getItemType() {
    return itemType;
  }

  public void setItemType(String itemType) {
    this.itemType = itemType;
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
}
