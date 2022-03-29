package iudx.aaa.server.apiserver;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static iudx.aaa.server.policy.Constants.NIL_UUID;

@DataObject(generateConverter = true)
public class CreatePolicyRequest {
    private UUID userId =  UUID.fromString(NIL_UUID);
    private String itemId;
    private String itemType;
    private String expiryTime = "";
    private JsonObject constraints;
    private String apdId = NIL_UUID;
    private String userClass;

    public String getUserId() {
        return userId.toString();
    }

    public void setUserId(String userId) {
        this.userId = UUID.fromString(userId);
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(String expiryTime) {
        this.expiryTime = expiryTime;
    }

    public JsonObject getConstraints() {
        return constraints;
    }

    public void setConstraints(JsonObject constraints) {
        this.constraints = constraints;
    }

    public String getApdId() {
        return apdId;
    }

    public void setApdId(String apdId) {
        this.apdId = apdId;
    }

    public String getUserClass() {
        return userClass;
    }

    public void setUserClass(String userClass) {
        this.userClass = userClass;
    }

    public CreatePolicyRequest(JsonObject json) {
        CreatePolicyRequestConverter.fromJson(json, this);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        CreatePolicyRequestConverter.toJson(this, obj);
        return obj;
    }

    public static List<CreatePolicyRequest> jsonArrayToList(JsonArray json) {
        List<CreatePolicyRequest> reg = new ArrayList<CreatePolicyRequest>();
        json.forEach(obj -> {
            reg.add(new CreatePolicyRequest((JsonObject) obj));
        });
        return reg;
    }

    @Override
    public String toString() {
        return
                "userId=" + userId +
                ", itemId='" + itemId + '\'' +
                ", itemType='" + itemType + '\'' +
                ", expiryTime='" + expiryTime + '\'' +
                ", constraints=" + constraints ;
    }
}