package iudx.aaa.server.apiserver.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UserAccessRequestDTO {

  @JsonProperty("user_email")
  private String userEmail;
  @JsonProperty("user_role")
  private String userRole;
  @JsonProperty("item_type")
  private String itemType;
  private List<String> capabilities;
  @JsonProperty("item_id")
  private String itemId;

  public String getUserEmail() {
    return userEmail;
  }

  public void setUserEmail(String userEmail) {
    this.userEmail = userEmail;
  }

  public String getUserRole() {
    return userRole;
  }

  public void setUserRole(String userRole) {
    this.userRole = userRole;
  }

  public String getItemType() {
    return itemType;
  }

  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public List<String> getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(List<String> capabilities) {
    this.capabilities = capabilities;
  }

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }



}
