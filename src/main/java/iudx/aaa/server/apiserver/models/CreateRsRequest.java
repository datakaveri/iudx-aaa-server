package iudx.aaa.server.apiserver.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Data object for create resource server API. The validations performed for url are minimal as the
 * Guava library validates the domain sufficiently.
 */
@DataObject(generateConverter = true)
public class CreateRsRequest {
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

  public CreateRsRequest(JsonObject json) {
    CreateRsRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    CreateRsRequestConverter.toJson(this, obj);
    return obj;
  }

  /**
   * Creates list of data objects from JsonArray
   *
   * @param json a JsonArray
   * @return List of CreateRsRequest objects
   */
  public static List<CreateRsRequest> jsonArrayToList(JsonArray json) {
    List<CreateRsRequest> reg = new ArrayList<CreateRsRequest>();
    json.forEach(obj -> reg.add(new CreateRsRequest((JsonObject) obj)));
    return reg;
  }
}
