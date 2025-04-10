package org.cdpg.dx.aaa.organization.models;


public enum Role {
  ADMIN("admin"),
  USER("member");

  private final String roleName;

  Role(String roleName) {
    this.roleName = roleName;
  }

  public String getRoleName() {
    return roleName;
  }

  private static Role temp;
  public static Role fromString(String roleStr) {
    for (Role role : Role.values()) {
      if (role.getRoleName().equalsIgnoreCase(roleStr))
       return role;
    }

    throw new IllegalArgumentException("Invalid role: " + roleStr);

  }

}

