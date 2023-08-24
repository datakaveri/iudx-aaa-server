package iudx.aaa.server.apiserver;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum that defines all valid roles recognized by the AAA server.
 */
public enum Roles {
  PROVIDER, DELEGATE, CONSUMER, ADMIN, COS_ADMIN;

  static List<String> rolesAsStrings =
      Arrays.stream(Roles.values()).map(r -> r.name()).collect(Collectors.toList());
  
  public static Set<Roles> allRoles = Set.of(Roles.values());

  /* function to check if a string is part of the enum without using try/catch */
  public static boolean exists(String str) {
    if (rolesAsStrings.contains(str)) {
      return true;
    }
    return false;
  }
}
