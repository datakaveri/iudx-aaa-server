package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

import static iudx.aaa.server.policy.Constants.APD;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.OWNER_ID;
import static iudx.aaa.server.policy.Constants.RESOURCE_GROUP_ID;
import static iudx.aaa.server.policy.Constants.RESOURCE_GROUP_TABLE;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_ID;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_TABLE;

public class ResourceObj {

  String itemType;
  UUID id;
  String catId;
  UUID ownerId;
  // null incase itemType is resServer
  UUID resServerID;
  // null incase itemType is resGrp/resServer
  UUID resGrpId;

  public ResourceObj(
      String itemType, UUID id, String catId, UUID ownerId, UUID resServerID, UUID resGrpId) {
    this.itemType = itemType;
    this.id = id;
    this.catId = catId;
    this.ownerId = ownerId;
    this.resServerID = resServerID;
    this.resGrpId = resGrpId;
  }

  public ResourceObj(JsonObject object) {
    this.itemType = object.getString(ITEMTYPE);
    this.id = UUID.fromString(object.getString(ID));
    this.catId = object.getString(CAT_ID);
    this.ownerId = UUID.fromString(object.getString(OWNER_ID));

    if (!object.getString(ITEMTYPE).equals(APD)) {
      if (!object.getString(ITEMTYPE).equals(RESOURCE_SERVER_TABLE)) {
        this.resServerID = UUID.fromString(object.getString(RESOURCE_SERVER_ID));
        if (!object.getString(ITEMTYPE).equals(RESOURCE_GROUP_TABLE))
          this.resGrpId = UUID.fromString(object.getString(RESOURCE_GROUP_ID));
        else this.resGrpId = UUID.fromString(object.getString(ID));
      }
    }
    else
    {
      this.resServerID = UUID.fromString(object.getString(RESOURCE_SERVER_ID));
      this.resGrpId = UUID.fromString(object.getString(RESOURCE_GROUP_ID));
    }
  }

  public String getItemType() {
    return itemType;
  }

  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCatId() {
    return catId;
  }

  public void setCatId(String catId) {
    this.catId = catId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public UUID getResServerID() {
    return resServerID;
  }

  public void setResServerID(UUID resServerID) {
    this.resServerID = resServerID;
  }

  public UUID getResGrpId() {
    return resGrpId;
  }

  public void setResGrpId(UUID resGrpId) {
    this.resGrpId = resGrpId;
  }

  @Override
  public String toString() {
    return "ResourceObj{" +
            "itemType='" + itemType + '\'' +
            ", id=" + id +
            ", catId='" + catId + '\'' +
            ", ownerId=" + ownerId +
            ", resServerID=" + resServerID +
            ", resGrpId=" + resGrpId +
            '}';
  }
}
