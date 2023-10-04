package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Data Object for Deletion of Delegations. */
@DataObject(generateConverter = true)
public class DeleteDelegationRequest {

  UUID id;

  public static List<DeleteDelegationRequest> jsonArrayToList(JsonArray json) {
    List<DeleteDelegationRequest> arr = new ArrayList<DeleteDelegationRequest>();
    json.forEach(
        obj -> {
          arr.add(new DeleteDelegationRequest((JsonObject) obj));
        });
    return arr;
  }

  public DeleteDelegationRequest(JsonObject json) {
    DeleteDelegationRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    DeleteDelegationRequestConverter.toJson(this, obj);
    return obj;
  }

  public String getId() {
    return id.toString();
  }

  public void setId(String id) {
    this.id = UUID.fromString(id);
  }
}
