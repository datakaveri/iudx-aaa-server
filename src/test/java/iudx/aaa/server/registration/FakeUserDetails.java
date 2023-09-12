package iudx.aaa.server.registration;

import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.Roles;
import static iudx.aaa.server.registration.Constants.NIL_PHONE;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Class to hold user information that is created when the fake user is added to the DB w/ roles.
 *
 */
public class FakeUserDetails {
  public String phone = NIL_PHONE;
  public JsonObject userInfo = new JsonObject();
  public String email = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";
  public String clientId;
  public String clientSecret;
  private Map<String, UUID> roleAndRsUrlToRoleId = new HashMap<String, UUID>();

  public FakeUserDetails(String phone, JsonObject userInfo) {
    this.phone = phone;
    this.userInfo = userInfo;
  }

  public FakeUserDetails() {}

  public UUID getRoleId(Roles role, String url) {
    return roleAndRsUrlToRoleId.get(role.toString() + url);
  }

  public void addRoleId(Roles role, String url, UUID id) {
    roleAndRsUrlToRoleId.put(role.toString() + url, id);
  }
}
