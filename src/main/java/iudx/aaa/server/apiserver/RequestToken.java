package iudx.aaa.server.apiserver;

import java.util.UUID;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import static iudx.aaa.server.apiserver.util.Constants.*;

@DataObject(generateConverter = true)
@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RequestToken {

  @JsonAlias({"clientId"})
  private UUID clientId = UUID.fromString(NIL_UUID);

  @JsonAlias("role")
  private String role;

  @JsonAlias("itemType")
  private String itemType;

  @JsonAlias("itemId")
  private String itemId;

  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    RequestTokenConverter.toJson(this, request);
    return request;
  }

  public RequestToken(JsonObject request) {
    RequestTokenConverter.fromJson(request, this);
  }

  public RequestToken() {}

  public String getClientId() {
    return clientId.toString();
  }

  public void setClientId(String clientId) {
    this.clientId = UUID.fromString(clientId);
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getItemType() {
    return itemType;
  }

  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }
}
