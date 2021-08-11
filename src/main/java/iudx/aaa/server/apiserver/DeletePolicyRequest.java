package iudx.aaa.server.apiserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * DataObjects for Deletion of Policy
 *
 */
@DataObject(generateConverter = true)
public class DeletePolicyRequest {

  UUID id;

  public static List<DeletePolicyRequest> jsonArrayToList(JsonArray json) {
    List<DeletePolicyRequest> arr = new ArrayList<DeletePolicyRequest>();
    json.forEach(obj -> {
      arr.add(new DeletePolicyRequest((JsonObject) obj));
    });
    return arr;
  }

  public DeletePolicyRequest(JsonObject json) {
    DeletePolicyRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    DeletePolicyRequestConverter.toJson(this, obj);
    return obj;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }
}
