package iudx.aaa.server.policy;

import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.ResourceObj;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.threeten.extra.AmountFormats;
import org.threeten.extra.PeriodDuration;

/**
 * The Email Info.
 *
 * <h1>Email Info</h1>
 *
 * <p>The Email Info object encompasses all the essential information needed to compose and send an
 * email.
 *
 * @version 1.0
 * @since 2023-04-17
 */
public class EmailInfo {

  UUID consumerId;
  List<CreatePolicyNotification> request;
  Map<String, ResourceObj> itemDetails;
  Map<String, List<UUID>> providerIdToAuthDelegateId;
  Map<String, JsonObject> userInfo;
  Map<String, String> expiryDurationMap;

  public EmailInfo() {
    this.expiryDurationMap = new HashMap<>();
  }

  public UUID getConsumerId() {
    return consumerId;
  }

  public void setConsumerId(UUID consumerId) {
    this.consumerId = consumerId;
  }

  public Map<String, String> getExpiryDurationMap() {
    return new HashMap<>(expiryDurationMap);
  }

  public void setExpiryDurationMap(String itemId, String duration) {
    PeriodDuration expiryDuration = PeriodDuration.parse(duration);
    expiryDurationMap.put(
        itemId,
        AmountFormats.wordBased(
            expiryDuration.getPeriod(), expiryDuration.getDuration(), Locale.ENGLISH));
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

  public void setItemDetails(Map<String, ResourceObj> itemDetails) {
    this.itemDetails = itemDetails;
  }

  public Map<String, List<UUID>> getProviderIdToAuthDelegateId() {
    return providerIdToAuthDelegateId;
  }

  public void setProviderIdToAuthDelegateId(Map<String, List<UUID>> providerIdToAuthDelegateId) {
    this.providerIdToAuthDelegateId = providerIdToAuthDelegateId;
  }

  public JsonObject getUserInfo(String uuid) {
    return userInfo.get(uuid);
  }

  public void setUserInfo(Map<String, JsonObject> userInfo) {
    this.userInfo = userInfo;
  }
}
