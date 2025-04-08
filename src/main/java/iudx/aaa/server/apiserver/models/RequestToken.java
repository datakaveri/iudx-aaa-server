package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ItemType;

/** Vert.x data object for the create token API. */
@DataObject(generateConverter = true)
public class RequestToken {

  private Roles role;

  private ItemType itemType;

  private String itemId;

  private JsonObject context = new JsonObject();

  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    RequestTokenConverter.toJson(this, request);
    return request;
  }

  public RequestToken(JsonObject request) {
    request.put("role", request.getString("role").toUpperCase());
    request.put("itemType", request.getString("itemType").toUpperCase());
    RequestTokenConverter.fromJson(request, this);
  }

  public RequestToken() {}

  public Roles getRole() {
    return role;
  }

  public void setRole(Roles role) {
    this.role = role;
  }

  public ItemType getItemType() {
    return itemType;
  }

  public void setItemType(ItemType itemType) {
    this.itemType = itemType;
  }

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId.toLowerCase();
  }

  public JsonObject getContext() {
    return context;
  }

  public void setContext(JsonObject context) {
    this.context = context;
  }
}
