package iudx.aaa.server.apiserver;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum that defines valid status an Access Policy Domain (APD) can be in.
 */
public enum ApdStatus {
  ACTIVE, INACTIVE;

  static List<String> apdStatusAsStrings =
      Arrays.stream(ApdStatus.values()).map(r -> r.name()).collect(Collectors.toList());

  /* function to check if a string is part of the enum without using try/catch */
  public static boolean exists(String str) {
    if (apdStatusAsStrings.contains(str)) {
      return true;
    }
    return false;
  }
}
