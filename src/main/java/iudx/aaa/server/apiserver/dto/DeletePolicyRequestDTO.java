package iudx.aaa.server.apiserver.dto;

import java.util.List;

public class DeletePolicyRequestDTO {

  private Integer id;
  private List<String> capabilities;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public List<String> getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(List<String> capabilities) {
    this.capabilities = capabilities;
  }


}
