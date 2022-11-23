package iudx.aaa.server.policy;

import java.time.LocalDateTime;
import java.util.UUID;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ResourceObj;

public class CreatePolicyContext {

  CreatePolicyRequest request;
  ResourceObj item;
  LocalDateTime expiryTime;
  ResourceObj apd;

  public CreatePolicyContext(CreatePolicyRequest request) {
    this.request = request;
  }

  public CreatePolicyRequest getRequest() {
    return request;
  }

  public void setRequest(CreatePolicyRequest request) {
    this.request = request;
  }

  public UUID getUserId() {
    return UUID.fromString(request.getUserId());
  }

  public ResourceObj getItem() {
    return item;
  }

  public void setItem(ResourceObj item) {
    this.item = item;
  }

  public LocalDateTime getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(LocalDateTime expiryTime) {
    this.expiryTime = expiryTime;
  }

  public JsonObject getConstraints() {
    return request.getConstraints();
  }

  public String getUserClass() {
    return request.getUserClass();
  }

  public ResourceObj getApd() {
    return apd;
  }

  public void setApd(ResourceObj apd) {
    this.apd = apd;
  }

}
