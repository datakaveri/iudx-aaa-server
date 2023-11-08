package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Data object for create Access Policy Domain (APD) API. The validations performed for url are
 * minimal as the Guava library validates the domain sufficiently.
 */
@DataObject(generateConverter = true)
public class CreateApdRequest {
  String name;
  String url;
  String owner;

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
    this.url = url.toLowerCase();
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner.toLowerCase();
  }

  public CreateApdRequest(JsonObject json) {
    CreateApdRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreateApdRequestConverter.toJson(this, obj);
    return obj;
  }
}
