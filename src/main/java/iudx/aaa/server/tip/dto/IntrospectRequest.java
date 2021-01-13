package iudx.aaa.server.tip.dto;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true, publicConverter = false)
public class IntrospectRequest {
  
  private String token;
  private String domain;
  private String resourceServerId;
  private HttpMethod method;
  private String endpoint;
  
  public IntrospectRequest(JsonObject json) {
    IntrospectRequestConverter.fromJson(json,this);
  }
  
  public JsonObject toJson() {
       return JsonObject.mapFrom(this);
  }
  
  public String getToken() {
    return token;
  }

  public String getDomain() {
    return domain;
  }

  public String getResourceServerId() {
    return resourceServerId;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public void setResourceServerId(String resourceServerId) {
    this.resourceServerId = resourceServerId;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((token == null) ? 0 : token.hashCode());
    return result;
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    IntrospectRequest other = (IntrospectRequest) obj;
    if (token == null) {
      if (other.token != null)
        return false;
    } else if (!token.equals(other.token))
      return false;
    return true;
  }
  
  
}
