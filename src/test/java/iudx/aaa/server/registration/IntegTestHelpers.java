package iudx.aaa.server.registration;

import org.apache.commons.lang3.RandomStringUtils;

public class IntegTestHelpers {

  public static String email() {
    return RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";
  }
  
}
