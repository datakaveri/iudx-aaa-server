package iudx.aaa.server.apiserver;

import java.util.UUID;

/**
 * Class to represent a resource group/resource item.
 *
 */
public class ResourceObj {

  ItemType itemType;
  UUID id;
  UUID ownerId;
  String resServerUrl;
  UUID resGrpId;
  String apdUrl;
  private String accessType;

  public ResourceObj(ItemType itemType, UUID id, UUID ownerId, String resServerUrl, UUID resGrpId,
      String apdUrl, String accessType) {
    this.itemType = itemType;
    this.id = id;
    this.ownerId = ownerId;
    this.resServerUrl = resServerUrl;
    this.resGrpId = resGrpId;
    this.apdUrl = apdUrl;
    this.accessType = accessType;
  }

  public ItemType getItemType() {
    return itemType;
  }

  public UUID getId() {
    return id;
  }
  public UUID getOwnerId() {
    return ownerId;
  }

  public String getResServerUrl() {
    return resServerUrl;
  }
  public UUID getResGrpId() {
    return resGrpId;
  }
  public String getApdUrl() {
    return apdUrl;
  }

  public Boolean isPii() {
    return accessType.equals("PII");
  }

  public static class ResourceObjBuilder
  {
    private ItemType itemType;
    private UUID id;
    private UUID ownerId;
    private String resServerUrl;
    private UUID resGrpId;
    private String apdUrl;
    private String accessType;

    public ResourceObjBuilder itemType(ItemType itemType) {
      this.itemType = itemType;
      return this;
    }

    public ResourceObjBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public ResourceObjBuilder ownerId(UUID ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    public ResourceObjBuilder resServerUrl(String resServerUrl) {
      this.resServerUrl = resServerUrl;
      return this;
    }

    public ResourceObjBuilder resGrpId(UUID resGrpId) {
      this.resGrpId = resGrpId;
      return this;
    }

    public ResourceObjBuilder apdUrl(String apdUrl) {
      this.apdUrl = apdUrl;
      return this;
    }

    public ResourceObjBuilder accessType(String accessType) {
      this.accessType = accessType;
      return this;
    }

    public ResourceObj build() {
      return new ResourceObj(this.itemType, this.id, this.ownerId, this.resServerUrl,
          this.resGrpId, this.apdUrl, this.accessType);
    }
  }

}
