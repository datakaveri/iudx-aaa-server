package iudx.aaa.server.apiserver;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum that defines all valid roles recognized by the AAA server.
 */
public enum Roles {
  PROVIDER, DELEGATE, TRUSTEE, CONSUMER, ADMIN;

  static List<String> rolesAsStrings =
      Arrays.stream(Roles.values()).map(r -> r.name()).collect(Collectors.toList());

  /* function to check if a string is part of the enum without using try/catch */
  public static boolean exists(String str) {
    if (rolesAsStrings.contains(str)) {
      return true;
    }
    return false;
  }
}
