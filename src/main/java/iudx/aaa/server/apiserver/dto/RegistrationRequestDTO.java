package iudx.aaa.server.apiserver.dto;

import java.util.List;

public class RegistrationRequestDTO {

  private NameDTO name;
  private int organization;
  private String phone;
  private String csr;
  private String email;
  private List<String> roles;

  public NameDTO getName() {
    return name;
  }

  public void setName(NameDTO name) {
    this.name = name;
  }

  public int getOrganization() {
    return organization;
  }

  public void setOrganization(int organization) {
    this.organization = organization;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getCsr() {
    return csr;
  }

  public void setCsr(String csr) {
    this.csr = csr;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

}
