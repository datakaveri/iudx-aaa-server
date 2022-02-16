package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data object for create Access Policy Domain (APD) API. The validations performed for url are
 * minimal as the Guava library validates the domain sufficiently.
 *
 */
@DataObject(generateConverter = true)
public class CreateApdRequest {
  String name;
  String url;

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

  public CreateApdRequest(JsonObject json) {
    CreateApdRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreateApdRequestConverter.toJson(this, obj);
    return obj;
  }
}
