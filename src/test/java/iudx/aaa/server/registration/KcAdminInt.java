package iudx.aaa.server.registration;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Keycloak admin client that allows for user creation, getting tokens for users from Keycloak and
 * deleting using automatically. The automatic deletion is handled by JUnit - this class implements
 * {@link CloseableResource} - so when tests are over, the {@link #close()} method is automatically
 * called which deletes users from Keycloak.
 */
public class KcAdminInt extends KcAdmin implements CloseableResource {

  private static final Logger LOGGER = LogManager.getLogger(KcAdminInt.class);

  public static final String FIRST_NAME = "firstNameIntegrationTestUser";
  public static final String LAST_NAME = "lastNameIntegrationTestUser";
  public static final String PASSWORD = "password";

  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";

  public static final String TOKEN_ENDPOINT = "/protocol/openid-connect/token";
  public static final String COS_ADMIN_EMAIL_PROPERTY = "COS_ADMIN_EMAIL";

  public static final String INTEG_CONFIG_PATH = "./configs/config-integ.json";

  private String tokenUrl;
  public String cosAdminToken;

  private KcAdminInt(
      String serverUrl,
      String realm,
      String clientId,
      String clientSecret,
      int poolSize,
      String cosAdminEmail) {
    super(serverUrl, realm, clientId, clientSecret, poolSize);

    this.tokenUrl = serverUrl + "/realms/" + realm + TOKEN_ENDPOINT;
    this.cosAdminToken = getToken(cosAdminEmail);

    LOGGER.info("Created KcAdminInt object successfully");
  }

  public static KcAdminInt init() throws IOException {
    String aconf = Files.readString(Path.of(INTEG_CONFIG_PATH));

    // get some configs from server config itself
    JsonObject config =
        new JsonObject(aconf).getJsonObject("options").getJsonObject("keycloakOptions");

    String keycloakUrl = config.getString(KEYCLOAK_URL);
    String keycloakRealm = config.getString(KEYCLOAK_REALM);
    String keycloakAdminClientId = config.getString(KC_ADMIN_CLIENT_ID);
    String keycloakAdminClientSecret = config.getString(KC_ADMIN_CLIENT_SEC);
    int keycloakAdminPoolSize = Integer.parseInt(config.getString(KC_ADMIN_POOLSIZE));

    Properties integrationTestEntities = new Properties();
    integrationTestEntities.load(
        KcAdminInt.class
            .getClassLoader()
            .getResourceAsStream("IntegrationTestEntities.properties"));

    String cosAdminEmail = integrationTestEntities.getProperty(COS_ADMIN_EMAIL_PROPERTY);

    return new KcAdminInt(
        keycloakUrl,
        keycloakRealm,
        keycloakAdminClientId,
        keycloakAdminClientSecret,
        keycloakAdminPoolSize,
        cosAdminEmail);
  }

  /**
   * Create a user on Keycloak. The user has password {@value #PASSWORD}. The email address passed
   * in is set as the email address. Once user is created successfully, a token for the user is
   * obtained and returned
   *
   * @param email the email of the user
   * @return a valid Keycloak token for the user
   */
  public String createUser(String email) {
    CredentialRepresentation passwordCred = new CredentialRepresentation();
    passwordCred.setTemporary(false);
    passwordCred.setType(CredentialRepresentation.PASSWORD);
    passwordCred.setValue(PASSWORD);

    UserRepresentation user = new UserRepresentation();
    user.setEnabled(true);
    user.setEmailVerified(true);
    user.setFirstName(FIRST_NAME);
    user.setLastName(LAST_NAME);
    user.setEmail(email);
    user.setCredentials(List.of(passwordCred));

    keycloak.realm(realm).users().create(user);
    return getToken(email);
  }

  /**
   * Get a valid Keycloak token for a user.
   *
   * @param email the email address of the user
   * @return the token
   */
  public String getToken(String email) {
    HttpClient client = HttpClient.newHttpClient();

    Map<String, String> parameters =
        Map.of(
            "client_id",
            "account",
            "username",
            email,
            "password",
            PASSWORD,
            "grant_type",
            "password",
            "scope",
            "email openid");

    String form =
        parameters.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form))
            .build();

    String tokenResponse = "";
    try {
      HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());
      tokenResponse = resp.body();
    } catch (IOException | InterruptedException e1) {
      e1.printStackTrace();
    }

    JsonObject tokenResponseJson = new JsonObject(tokenResponse);

    return tokenResponseJson.getString("access_token");
  }

  /**
   * Delete users created for all integration tests from Keycloak. Any user with first name {@value
   * #FIRST_NAME} will be deleted.
   */
  private void deleteUsers() {
    UsersResource userResource = keycloak.realm(realm).users();

    List<UserRepresentation> users = userResource.search(FIRST_NAME, 0, -1);
    LOGGER.info("Deleting {} users from Keycloak", users.size());

    users.forEach(
        i -> {
          userResource.get(i.getId()).remove();
        });
    LOGGER.info("Deleted users successfully!");
  }

  @Override
  public void close() throws Throwable {
    LOGGER.info("Integration tests over - about to delete created users in Keycloak");
    deleteUsers();
  }
}
