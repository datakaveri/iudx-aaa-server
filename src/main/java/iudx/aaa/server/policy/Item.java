package iudx.aaa.server.policy;

import java.util.Set;

public class Item {
  
  public String id;
  public String type;
  public String providerID;
  public Set<String> servers; 
  
  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }
  public String getType() {
    return type;
  }
  public void setType(String type) {
    this.type = type;
  }
  public String getProviderID() {
    return providerID;
  }
  public void setProviderID(String providerID) {
    this.providerID = providerID;
  }
  public Set<String> getServers() {
    return servers;
  }
  public void setServers(Set<String> servers) {
    this.servers = servers;
  }
}
