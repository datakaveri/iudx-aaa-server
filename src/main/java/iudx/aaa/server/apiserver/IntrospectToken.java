package iudx.aaa.server.apiserver;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class IntrospectToken {

  @JsonAlias("accessToken")
  private String accessToken;

  public JsonObject toJson() {
    JsonObject request = new JsonObject();
    IntrospectTokenConverter.toJson(this, request);
    return request;
  }

  public IntrospectToken(JsonObject request) {
    IntrospectTokenConverter.fromJson(request, this);
  }

  public IntrospectToken() {}

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
