package iudx.aaa.server.apiserver.dto;

import java.util.List;

public class TokenRequestDTO {
  private List<TokeRequestParam> request;

  public List<TokeRequestParam> getRequest() {
    return request;
  }

  public void setRequest(List<TokeRequestParam> request) {
    this.request = request;
  }

  class TokeRequestParam {
    private String id;
    private List<String> apis;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public List<String> getApis() {
      return apis;
    }

    public void setApis(List<String> apis) {
      this.apis = apis;
    }
  }
}


