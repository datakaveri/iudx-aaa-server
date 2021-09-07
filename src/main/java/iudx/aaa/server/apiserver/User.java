package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.util.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User object.
 */
@DataObject(generateConverter = true)
public class User {

  Map<String, String> name = new HashMap<String, String>();

  UUID userId;
  UUID keycloakId;
  List<Roles> roles; //roles is list of approved roles

  public String getKeycloakId() {
    return this.keycloakId.toString();
  }

  public void setKeycloakId(String keycloakId) {
    this.keycloakId = UUID.fromString(keycloakId);
  }

  public User(UserBuilder builder) {
    this.name.putAll(builder.name);
    this.keycloakId = builder.keycloakId;
    this.userId = builder.userId;
    this.roles = builder.roles;
  }

  public User(JsonObject json) {
    UserConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    UserConverter.toJson(this, obj);
    return obj;
  }

  /**
   * Used when the User object needs to be sent in the
   * response, so that roles are changed to lowercase.
   * 
   * @return JsonObject the User object in JSON
   */
  public JsonObject toJsonResponse() {
    JsonObject obj = this.toJson();
    @SuppressWarnings("unchecked")
    List<String> roles = obj.getJsonArray("roles").getList();
    roles.replaceAll(String::toLowerCase);
    obj.put("roles", roles);
    return obj;
  }

  public Map<String, String> getName() {
    return name;
  }

  public void setName(Map<String, String> name) {
    this.name = name;
  }

  public String getUserId() {
    return userId.toString();
  }

  public void setUserId(String userId) {
    this.userId = UUID.fromString(userId);
  }

  public List<Roles> getRoles() {
    return roles;
  }

  public void setRoles(List<Roles> roles) {
    this.roles = roles;
  }

  public static class UserBuilder {
    private Map<String, String> name = new HashMap<String, String>();

    private UUID userId = UUID.fromString(Constants.NIL_UUID);
    private UUID keycloakId = UUID.fromString(Constants.NIL_UUID);

    private List<Roles> roles = new ArrayList<Roles>();

    public UserBuilder name(String firstName, String lastName) {
      this.name.put("firstName", firstName);
      this.name.put("lastName", lastName);
      return this;
    }

    public UserBuilder userId(String userId) {
      this.userId = UUID.fromString(userId);
      return this;
    }

    public UserBuilder userId(UUID userId) {
      this.userId = userId;
      return this;
    }

    public UserBuilder keycloakId(String keycloakId) {
      this.keycloakId = UUID.fromString(keycloakId);
      return this;
    }

    public UserBuilder keycloakId(UUID keycloakId) {
      this.keycloakId = keycloakId;
      return this;
    }

    public UserBuilder roles(List<Roles> roles) {
      this.roles = roles;
      return this;
    }

    public User build() {
      User user = new User(this);
      return user;
    }
  }
}
