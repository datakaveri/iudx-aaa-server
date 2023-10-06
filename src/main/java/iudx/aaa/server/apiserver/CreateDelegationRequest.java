package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Vert.x data object for the create delegation API. */
@DataObject(generateConverter = true)
public class CreateDelegationRequest {
  private String userEmail;
  private String resSerUrl;
  private Roles role;

  public String getUserEmail() {
    return userEmail.toString();
  }

  public void setUserEmail(String userEmail) {
    this.userEmail = userEmail.toLowerCase();
  }

  public String getResSerUrl() {
    return resSerUrl;
  }

  public void setResSerUrl(String resSerUrl) {
    this.resSerUrl = resSerUrl.toLowerCase();
  }

  public Roles getRole() {
    return role;
  }

  public void setRole(Roles role) {
    this.role = role;
  }

  public CreateDelegationRequest(JsonObject json) {
    json.put("role", json.getString("role").toUpperCase());
    CreateDelegationRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreateDelegationRequestConverter.toJson(this, obj);
    return obj;
  }

  public static List<CreateDelegationRequest> jsonArrayToList(JsonArray json) {
    List<CreateDelegationRequest> reg = new ArrayList<>();
    json.forEach(
        obj -> {
          reg.add(new CreateDelegationRequest((JsonObject) obj));
        });
    return reg;
  }
}
