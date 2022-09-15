
package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DataObject(generateConverter = true)
public class DeletePolicyNotificationRequest {

    UUID id;

    public static List<DeletePolicyNotificationRequest> jsonArrayToList(JsonArray json) {
        List<DeletePolicyNotificationRequest> arr = new ArrayList<DeletePolicyNotificationRequest>();
        json.forEach(obj -> {
            arr.add(new DeletePolicyNotificationRequest((JsonObject) obj));
        });
        return arr;
    }

    public DeletePolicyNotificationRequest(JsonObject json) {
        DeletePolicyNotificationRequestConverter.fromJson(json,this);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        DeletePolicyNotificationRequestConverter.toJson(this, obj);
        return obj;
    }
    public String getId() {
        return id.toString();
    }

    public void setId(String id) {
        this.id = UUID.fromString(id);
    }
}

