package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * DataObjects for creating Policy for a User.
 *
 */
@DataObject(generateConverter = true)
public class CreatePolicyRequest {

  UUID userId;
  String resId;
  String resType;
  String expiryTime;
  JsonObject constraints;

  public static List<CreatePolicyRequest> jsonArrayToList(JsonArray json) {
    List<CreatePolicyRequest> arr = new ArrayList<CreatePolicyRequest>();
    json.forEach(obj -> {
      arr.add(new CreatePolicyRequest((JsonObject) obj));
    });
    return arr;
  }

  public CreatePolicyRequest(JsonObject json) {
    CreatePolicyRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreatePolicyRequestConverter.toJson(this, obj);
    return obj;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getResId() {
    return resId;
  }

  public void setResId(String resId) {
    this.resId = resId;
  }

  public String getResType() {
    return resType;
  }

  public void setResType(String resType) {
    this.resType = resType;
  }

  public String getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(String expiryTime) {
    this.expiryTime = expiryTime;
  }

  public JsonObject getConstraints() {
    return constraints;
  }

  public void setConstraints(JsonObject constraints) {
    this.constraints = constraints;
  }
}
