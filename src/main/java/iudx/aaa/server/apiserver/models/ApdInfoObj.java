package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/** Data object to hold APD information. */
@DataObject(generateConverter = true)
public class ApdInfoObj {
  UUID id;
  String name;
  String url;
  String ownerId;
  ApdStatus status;

  public String getId() {
    return id.toString();
  }

  public void setId(String id) {
    this.id = UUID.fromString(id);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public ApdStatus getStatus() {
    return status;
  }

  public void setStatus(ApdStatus status) {
    this.status = status;
  }

  public ApdInfoObj(UUID id, String name, String url, UUID ownerId, ApdStatus status) {
    this.id = id;
    this.name = name;
    this.url = url;
    this.ownerId = ownerId.toString();
    this.status = status;
  }

  public ApdInfoObj(JsonObject json) {
    ApdInfoObjConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    ApdInfoObjConverter.toJson(this, obj);
    return obj;
  }
}
