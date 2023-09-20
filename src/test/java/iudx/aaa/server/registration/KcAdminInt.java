package iudx.aaa.server.registration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.Response;

public class KcAdminInt extends KcAdmin {
  public static final String FIRST_NAME = "firstName";
  public static final String LAST_NAME = "lastName";
  public static final String PASSWORD = "password";
  
  public static final String KEYCLOAK_URL = "keycloakUrl";
  public static final String KEYCLOAK_REALM = "keycloakRealm";
  public static final String KC_ADMIN_CLIENT_ID = "keycloakAdminClientId";
  public static final String KC_ADMIN_CLIENT_SEC = "keycloakAdminClientSecret";
  public static final String KC_ADMIN_POOLSIZE = "keycloakAdminPoolSize";
  public static final String KEYCLOAK_SITE_URL = "keycloakSite";
  
  public static final String TOKEN_ENDPOINT = "/protocol/openid-connect/token";
  
  private String tokenUrl;
  public String cosAdminToken;

  private KcAdminInt(String serverUrl, String realm, String clientId, String clientSecret,
      int poolSize, String keycloakSiteUrl) {
    super(serverUrl, realm, clientId, clientSecret, poolSize);
    
    this.tokenUrl = keycloakSiteUrl + TOKEN_ENDPOINT;
    this.cosAdminToken = getToken("auth.admin@datakaveri.org");
  }

  public static KcAdminInt init() throws IOException {
    String aconf = Files.readString(Path.of("./configs/config-integ.json"));
    JsonObject config = new JsonObject(aconf).getJsonObject("options").getJsonObject("keycloakOptions");
    String keycloakUrl = config.getString(KEYCLOAK_URL);
    String keycloakRealm = config.getString(KEYCLOAK_REALM);
    String keycloakAdminClientId = config.getString(KC_ADMIN_CLIENT_ID);
    String keycloakAdminClientSecret = config.getString(KC_ADMIN_CLIENT_SEC);
    int keycloakAdminPoolSize = Integer.parseInt(config.getString(KC_ADMIN_POOLSIZE));
    
    String keycloakSiteUrl = config.getString(KEYCLOAK_SITE_URL);
    return new KcAdminInt(keycloakUrl, keycloakRealm, keycloakAdminClientId,
        keycloakAdminClientSecret, keycloakAdminPoolSize, keycloakSiteUrl);
  }

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
  
  public String getToken(String email) {
    HttpClient client = HttpClient.newHttpClient();
    
    Map<String, String> parameters = Map.of("client_id", "account", "username", email, "password",
        PASSWORD, "grant_type", "password", "scope", "email openid");

    String form = parameters.entrySet()
        .stream()
        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
    
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(tokenUrl))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(form))
        .build();
    
    String op = "";
    try {
      HttpResponse<String> resp = client.send(request, BodyHandlers.ofString());
      op = resp.body();
    } catch (IOException | InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    
    JsonObject oo = new JsonObject(op);
    
    return oo.getString("access_token");
  }

}
