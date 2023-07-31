package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.util.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User object.
 */
@DataObject(generateConverter = true)
public class User {

  Map<String, String> name = new HashMap<String, String>();

  UUID userId;
  List<Roles> roles; // roles is list of approved roles
  Map<String, List<String>> rolesToRsMapping;

  public User(UserBuilder builder) {
    this.name.putAll(builder.name);
    this.userId = builder.userId;
    this.roles = builder.roles;
    this.rolesToRsMapping = new HashMap<String, List<String>>(builder.rolesToRsMapping);
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
   * Used when the User object needs to be sent in the response, so that roles are changed to
   * lowercase.
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

  public List<String> getResServersForRole(Roles role) {
    return rolesToRsMapping.get(role.toString().toLowerCase());
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

  public Map<String, JsonArray> getRolesToRsMapping() {
    return rolesToRsMapping.entrySet().stream()
        .collect(Collectors.toMap(i -> i.getKey().toLowerCase(), i -> new JsonArray(i.getValue())));
  }

  @SuppressWarnings("unchecked")
  public void setRolesToRsMapping(Map<String, JsonArray> rolesToRsMapping) {
    this.rolesToRsMapping = rolesToRsMapping.entrySet().stream()
        .collect(Collectors.toMap(i -> i.getKey().toLowerCase(), i -> i.getValue().getList()));
  }

  public static class UserBuilder {
    private Map<String, String> name = new HashMap<String, String>();

    private UUID userId = UUID.fromString(Constants.NIL_UUID);

    private List<Roles> roles = new ArrayList<Roles>();

    private Map<String, List<String>> rolesToRsMapping = new HashMap<String, List<String>>();

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

    public UserBuilder roles(List<Roles> roles) {
      this.roles = roles;
      return this;
    }

    @SuppressWarnings("unchecked")
    public UserBuilder rolesToRsMapping(Map<String, JsonArray> rolesToRsMapping) {
      this.rolesToRsMapping = rolesToRsMapping.entrySet().stream()
          .collect(Collectors.toMap(i -> i.getKey().toLowerCase(), i -> i.getValue().getList()));
      return this;
    }

    public User build() {
      User user = new User(this);
      return user;
    }
  }
}
