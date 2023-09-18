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
public class AddRolesRequest {

  String phone = Constants.NIL_PHONE;
  List<String> consumer = new ArrayList<String>();
  List<String> provider = new ArrayList<String>();
  JsonObject userInfo = new JsonObject();

  public AddRolesRequest(JsonObject json) {
    AddRolesRequestConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    AddRolesRequestConverter.toJson(this, obj);
    return obj;
  }

  public String getPhone() {
    return this.phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public List<String> getConsumer() {
    return new ArrayList<String>(this.consumer);
  }

  public void setConsumer(List<String> consumer) {
    this.consumer = consumer.stream().map(String::toLowerCase).collect(Collectors.toList());
  }

  public List<String> getProvider() {
    return new ArrayList<String>(this.provider);
  }

  public void setProvider(List<String> provider) {
    this.provider = provider.stream().map(String::toLowerCase).collect(Collectors.toList());
  }

  public JsonObject getUserInfo() {
    return userInfo;
  }

  public void setUserInfo(JsonObject userInfo) {
    this.userInfo = userInfo;
  }

  public List<Roles> getRolesToRegister() {
    List<Roles> roles = new ArrayList<Roles>();
    if (!consumer.isEmpty()) {
      roles.add(Roles.CONSUMER);
    }
    if (!provider.isEmpty()) {
      roles.add(Roles.PROVIDER);
    }

    return roles;
  }

}
