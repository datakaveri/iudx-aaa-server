package iudx.aaa.server.apiserver;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

/**
 * Holds information pertaining to a delegation - derived from the delegationId header + user.
 * It will be created by {@link DelegationIdAuthorization} and put onto the routing context.
 *
 */
@DataObject(generateConverter = true)
public class DelegationInformation {

  UUID delegationId;
  UUID delegatorUserId;
  Roles delegatedRole;
  String delegatedRsUrl;

  public DelegationInformation(JsonObject json) {
    DelegationInformationConverter.fromJson(json, this);
  }
  
  public DelegationInformation(UUID delegationId, UUID delegatorUserId, Roles delegatedRole,
      String delegatedRsUrl) {
    this.delegationId = delegationId;
    this.delegatorUserId = delegatorUserId;
    this.delegatedRole = delegatedRole;
    this.delegatedRsUrl = delegatedRsUrl;
  }

  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    DelegationInformationConverter.toJson(this, obj);
    return obj;
  }

  public String getDelegationId() {
    return delegationId.toString();
  }

  public void setDelegationId(String delegationId) {
    this.delegationId = UUID.fromString(delegationId);
  }

  public String getDelegatorUserId() {
    return delegatorUserId.toString();
  }

  public void setDelegatorUserId(String delegatorUserId) {
    this.delegatorUserId = UUID.fromString(delegatorUserId);
  }

  public Roles getDelegatedRole() {
    return delegatedRole;
  }

  public void setDelegatedRole(Roles delegatedRole) {
    this.delegatedRole = delegatedRole;
  }

  public String getDelegatedRsUrl() {
    return delegatedRsUrl;
  }

  public void setDelegatedRsUrl(String delegatedRsUrl) {
    this.delegatedRsUrl = delegatedRsUrl;
  }

}
