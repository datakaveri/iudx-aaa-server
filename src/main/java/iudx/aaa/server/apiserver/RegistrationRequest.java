package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.registration.Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

  static public RegistrationRequest validatedObj(JsonObject json) {
    return new RegistrationRequest(validateJsonObject(json));
  }

  /**
   * <b>Do not use this constructor for creating object.
   * Use validatedObj function</b>
   * @param json
   */
  public RegistrationRequest(JsonObject json) {
    RegistrationRequestConverter.fromJson(json, this);
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

  private static JsonObject validateJsonObject(JsonObject json) throws IllegalArgumentException {

    if (!json.containsKey("roles") || !(json.getValue("roles") instanceof JsonArray)) {
      throw new IllegalArgumentException("'roles' array is required");
    }

    if (json.containsKey("orgId")) {
      Object orgId = json.getValue("orgId");

      if (!(orgId instanceof String)) {
        throw new IllegalArgumentException("Invalid 'orgId'");
      }

      String castedOrgId = String.valueOf(orgId).toLowerCase();
      if (!castedOrgId
          .matches("^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$")) {
        throw new IllegalArgumentException("Invalid 'orgId'");
      }
    }

    if (json.containsKey("phone")) {
      Object phone = json.getValue("phone");

      if (!(phone instanceof String)) {
        throw new IllegalArgumentException("Invalid 'phone'");
      }

      String castedPhone = String.valueOf(phone).toLowerCase();
      if (!castedPhone.matches("^[9876]\\d{9}$")) {
        throw new IllegalArgumentException("Invalid 'phone'");
      }
    }

    JsonArray roles = json.getJsonArray("roles");
    Set<String> set = new HashSet<String>();

    roles.forEach(x -> {
      if (!(x instanceof String)) {
        throw new IllegalArgumentException("Invalid 'roles' array");
      }

      String str = ((String) x).toUpperCase();

      if (!Roles.exists(str)) {
        throw new IllegalArgumentException("Invalid 'roles' array");
      }
      set.add(str);
    });

    if (set.contains(Roles.ADMIN.name())) {
      throw new IllegalArgumentException("Invalid 'roles' array");
    }

    json.remove("roles");
    json.put("roles", new ArrayList<String>(set));

    return json;
  }
}
