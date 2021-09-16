package iudx.aaa.server.apiserver;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class CreateDelegationRequest {
    private UUID userId;
    private String resSerId;

    public String getUserId() {
        return userId.toString();
    }

    public void setUserId(String userId) {
        this.userId = UUID.fromString(userId);
    }


    public String getResSerId() {
        return resSerId;
    }

    public void setResSerId(String resSerId) {
        this.resSerId = resSerId;
    }

    public CreateDelegationRequest(JsonObject json) {
        CreateDelegationRequestConverter.fromJson(json, this);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        CreateDelegationRequestConverter.toJson(this, obj);
        return obj;
    }

    public static List<CreateDelegationRequest> jsonArrayToList(JsonArray json) {
        List<CreateDelegationRequest> reg = new ArrayList<>();
        json.forEach(obj -> {
            reg.add(new CreateDelegationRequest((JsonObject) obj));
        });
        return reg;
    }

    @Override
    public String toString() {
        return "CreateDelegationRequest{" +
                "userId=" + userId +
                ", resSerId='" + resSerId + '\'' +
                '}';
    }
}