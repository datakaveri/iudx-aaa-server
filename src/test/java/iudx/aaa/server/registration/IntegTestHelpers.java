package iudx.aaa.server.registration;

import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.lang3.RandomStringUtils;

/** Helper methods for integration tests. */
public class IntegTestHelpers {

  /**
   * Generate a random email address with <code>@gmail.com</code> in lowercase.
   *
   * @return random email address
   */
  public static String email() {
    return RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";
  }

  /**
   * Extract user ID from the given Keycloak token. The ID is present in the <code>sub</code> key.
   *
   * @param token the Keycloak token
   * @return the user ID (as a UUID)
   */
  public static String getUserIdFromKcToken(String token) {
    String payload = token.split("\\.")[1];
    byte[] bytes = Base64.getUrlDecoder().decode(payload);
    return new JsonObject(new String(bytes, StandardCharsets.UTF_8)).getString("sub");
  }
}
