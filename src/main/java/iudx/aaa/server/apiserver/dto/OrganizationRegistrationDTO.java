package iudx.aaa.server.apiserver.dto;

public class OrganizationRegistrationDTO {

  private String organization;

  public String getOrganization() {
    return organization;
  }

  public void setOrganization(String organization) {
    this.organization = organization;
  }


  class Organization {
    private String name;
    private String website;
    private String city;
    private String state;
    private String country;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getWebsite() {
      return website;
    }

    public void setWebsite(String website) {
      this.website = website;
    }

    public String getCity() {
      return city;
    }

    public void setCity(String city) {
      this.city = city;
    }

    public String getState() {
      return state;
    }

    public void setState(String state) {
      this.state = state;
    }

    public String getCountry() {
      return country;
    }

    public void setCountry(String country) {
      this.country = country;
    }

  }

}
