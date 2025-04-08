package iudx.aaa.server.apiserver.models;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Enum that defines valid status a role can be in. */
public enum RoleStatus {
  PENDING,
  APPROVED,
  REJECTED;

  static List<String> roleStatusAsStrings =
      Arrays.stream(RoleStatus.values()).map(r -> r.name()).collect(Collectors.toList());

  /* function to check if a string is part of the enum without using try/catch */
  public static boolean exists(String str) {
    if (roleStatusAsStrings.contains(str)) {
      return true;
    }
    return false;
  }
}
