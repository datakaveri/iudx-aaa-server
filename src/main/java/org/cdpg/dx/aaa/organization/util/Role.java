package org.cdpg.dx.aaa.organization.util;


public enum Role {
  ADMIN("admin"),
  USER("user");

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
        temp=role;
    }

    return temp;

  }

}

