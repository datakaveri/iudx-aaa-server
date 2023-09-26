package iudx.aaa.server.registration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import io.vertx.core.json.JsonObject;

public class IntegTestHelpers {

  public static String email() {
    return RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";
  }
  
  public static String getUserIdFromKcToken(String token)
  {
    String payload = token.split("\\.")[1];
    byte[] bytes = Base64.getUrlDecoder().decode(payload);
    return new JsonObject(new String(bytes, StandardCharsets.UTF_8)).getString("sub");
  }
  
}
