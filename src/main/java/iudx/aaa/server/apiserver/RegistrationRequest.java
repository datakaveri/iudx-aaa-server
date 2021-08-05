package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.registration.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vert.x data object for the createUser API in Registration service.
 */
@DataObject(generateConverter = true)
public class RegistrationRequest {

  String phone = Constants.NIL_PHONE;
  UUID orgId = UUID.fromString(Constants.NIL_UUID);
  List<Roles> roles = new ArrayList<Roles>();

  public List<Roles> getRoles() {
    return roles;
  }

  public void setRoles(List<Roles> roles) {
    this.roles = roles;
  }

  public RegistrationRequest(JsonObject json) {
    RegistrationRequestConverter.fromJson(rolesToUpperCase(json), this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    RegistrationRequestConverter.toJson(this, obj);
    return obj;
  }

  public String getPhone() {
    return this.phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getOrgId() {
    return this.orgId.toString();
  }

  public void setOrgId(String orgId) {
    this.orgId = UUID.fromString(orgId);
  }

  private static JsonObject rolesToUpperCase(JsonObject json) {
    JsonArray roles = json.getJsonArray("roles");
    json.remove("roles");
    json.put("roles",
        roles.stream().map(i -> ((String) i).toUpperCase()).collect(Collectors.toList()));

    return json;
  }
}
