package iudx.aaa.server.apiserver;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RevokeToken {

  @JsonAlias("clientId")
  private String clientId;

  @JsonAlias("rsUrl")
  private String rsUrl;


  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    RevokeTokenConverter.toJson(this, request);
    return request;
  }

  public RevokeToken(JsonObject request) {
    RevokeTokenConverter.fromJson(request, this);
  }

  public RevokeToken() {}

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getRsUrl() {
    return rsUrl;
  }

  public void setRsUrl(String rsUrl) {
    this.rsUrl = rsUrl;
  }

}
