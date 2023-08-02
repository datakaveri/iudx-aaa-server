package iudx.aaa.server.apiserver;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class CreateDelegationRequest {
    private String userEmail;
    private String resSerUrl;
    private Roles role;

    public String getUserEmail() {
        return userEmail.toString();
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getResSerUrl() {
        return resSerUrl;
    }

    public void setResSerUrl(String resSerUrl) {
        this.resSerUrl = resSerUrl;
    }

    public Roles getRole() {
      return role;
    }

    public void setRole(Roles role) {
      this.role = role;
    }

    public CreateDelegationRequest(JsonObject json) {
      json.put("role", json.getString("role").toUpperCase());
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
                "userEmail=" + userEmail +
                ", resSerUrl='" + resSerUrl + '\'' +
                '}';
    }
}