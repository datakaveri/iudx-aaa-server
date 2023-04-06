package iudx.aaa.server.policy;

import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.ResourceObj;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EmailInfo {

  UUID consumerId;
  List<CreatePolicyNotification> request;
  Map<String, ResourceObj> itemDetails;
  Map<UUID, List<UUID>> providerIdToAuthDelegateId;
  Map<String, JsonObject> userInfo;

  public UUID getConsumerId() {
    return consumerId;
  }

  public void setConsumerId(UUID consumerId) {
    this.consumerId = consumerId;
  }

  public List<CreatePolicyNotification> getRequest() {
    return request;
  }

  public void setRequest(List<CreatePolicyNotification> request) {
    this.request = request;
  }

  public Map<String, ResourceObj> getItemDetails() {
    return itemDetails;
  }

  public void setItemDetails(
      Map<String, ResourceObj> itemDetails) {
    this.itemDetails = itemDetails;
  }

  public Map<UUID, List<UUID>> getProviderIdToAuthDelegateId() {
    return providerIdToAuthDelegateId;
  }

  public void setProviderIdToAuthDelegateId(
      Map<UUID, List<UUID>> providerIdToAuthDelegateId) {
    this.providerIdToAuthDelegateId = providerIdToAuthDelegateId;
  }

  public Map<String, JsonObject> getUserInfo() {
    return userInfo;
  }

  public void setUserInfo(Map<String, JsonObject> userInfo) {
    this.userInfo = userInfo;
  }

}
