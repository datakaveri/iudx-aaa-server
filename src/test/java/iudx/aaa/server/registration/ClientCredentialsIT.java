package iudx.aaa.server.registration;

import org.junit.jupiter.api.extension.ExtendWith;
import static io.restassured.RestAssured.*;
import static iudx.aaa.server.admin.Constants.*;
import static iudx.aaa.server.registration.Constants.*;
import static iudx.aaa.server.token.Constants.*;
import static org.hamcrest.Matchers.*;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.registration.IntegTestHelpers;
import iudx.aaa.server.registration.KcAdminExtension;
import iudx.aaa.server.registration.KcAdminInt;

@ExtendWith(KcAdminExtension.class)
public class ClientCredentialsIT {

  /**
   * Token with admin role.
   */
  private static String adminToken;
  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  /**
   * Token with consumer role.
   */
  private static String consumerToken;
  private static String tokenNoRoles;

  @BeforeAll
  static void setup(KcAdminInt kc) {
    baseURI = "http://localhost";
    String portSet = System.getProperty("integrationTestPort");
    if (NumberUtils.isDigits(portSet)) {
      port = NumberUtils.createInteger(portSet);
    } else {
      port = 8443;
    }
    basePath = "/auth/v1";
    enableLoggingOfRequestAndResponseIfValidationFails();

    tokenNoRoles = kc.createUser(IntegTestHelpers.email());

    String adminEmail = IntegTestHelpers.email();
    adminToken = kc.createUser(adminEmail);
    consumerToken = kc.createUser(IntegTestHelpers.email());

    // create RS
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER).put("owner", adminEmail);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(rsReq.toString())
        .when().post("/admin/resourceservers").then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));

    // create consumer roles
    JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));

    given().auth().oauth2(consumerToken).contentType(ContentType.JSON).body(consReq.toString()).when()
        .post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer", is(200)));
  }

  @Nested
  @DisplayName("User with no roles cannot create or reset client creds")
  class noRolesCannotCall {

    @Test
    @DisplayName("Cannot get default creds")
    void cannotDefault() {

      given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).when()
          .get("/user/clientcredentials").then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
    }

    @Test
    @DisplayName("Cannot call regen")
    void cannotRegen() {
      JsonObject body = new JsonObject().put("clientId", UUID.randomUUID().toString());

      given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/user/clientcredentials").then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
    }
  }

  @Test
  @DisplayName("Regenerate client secret - missing body")
  void regenMissingBody() {
    given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).when()
          .put("/user/clientcredentials").then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Regenerate client secret - missing clientId key")
  void regenMissingKey() {
    JsonObject body = new JsonObject();

    given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).body(body.toString()).when()
    .put("/user/clientcredentials").then()
    .statusCode(400)
    .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Regenerate client secret - no token")
  void regenMissingToken() {
    JsonObject body = new JsonObject().put("clientId", UUID.randomUUID().toString());

    given().contentType(ContentType.JSON).body(body.toString()).when()
    .put("/user/clientcredentials").then()
    .statusCode(401)
    .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Get default client secret - no token")
  void defaultCliMissingToken() {

    given().contentType(ContentType.JSON).when()
    .get("/user/clientcredentials").then()
    .statusCode(401)
    .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Nested
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Test client credentials flow using admin")
  class AdminClientTests {

    String clientId;
    String clientSecret;
    String newClientSecret;

    JsonObject tokenBody = new JsonObject().put("itemId", DUMMY_SERVER)
        .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
        .put("role", Roles.ADMIN.toString().toLowerCase());
      
    @Test
    @Order(1)
    @DisplayName("Get Default creds")
    void getDefault() {

      String clientInfo = given().auth().oauth2(adminToken).contentType(ContentType.JSON).when()
          .get("/user/clientcredentials").then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_CREATED_DEFAULT_CLIENT.toString()))
          .body("results.clientName", equalTo(DEFAULT_CLIENT))
          .body("results", hasKey(RESP_CLIENT_ID))
          .body("results", hasKey(RESP_CLIENT_SC))
          .extract().response().asString();
      
      clientId = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
      clientSecret = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);
    }
    
    @Test
    @Order(2)
    @DisplayName("Get admin identity token using creds")
    void callTokenApi() {

      given().header("clientId", clientId).header("clientSecret", clientSecret)
          .contentType(ContentType.JSON).body(tokenBody.toString()).when()
          .post("/token").then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(TOKEN_SUCCESS.toString()))
          .body("results", hasKey(ACCESS_TOKEN))
          .body("results", hasKey("expiry"))
          .body("results", hasKey("server"))
          .extract().path("results");
    }

    @Test
    @Order(3)
    @DisplayName("Call regenerate client secret")
    void cannotRegen() {
      JsonObject body = new JsonObject().put("clientId", clientId);

      newClientSecret = given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/user/clientcredentials").then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_REGEN_CLIENT_SECRET.toString()))
          .body("results", hasKey(RESP_CLIENT_ARR))
          .body("results.clients", hasSize(1))
          .body("results.clients[0].clientId", equalTo(clientId))
          .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
          .body("results.clients[0].clientSecret", not(equalTo(clientSecret)))
          .extract().path("results.clients[0].clientSecret");
    }
    
    @Test
    @Order(4)
    @DisplayName("Old Creds don't work, new creds do")
    void oldCredsNotWork() {

      given().header("clientId", clientId).header("clientSecret", clientSecret)
          .contentType(ContentType.JSON).body(tokenBody.toString()).when()
          .post("/token").then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC.toString()))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC.toString()));
      
      given().header("clientId", clientId).header("clientSecret", newClientSecret)
          .contentType(ContentType.JSON).body(tokenBody.toString()).when()
          .post("/token").then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(TOKEN_SUCCESS.toString()))
          .body("results", hasKey(ACCESS_TOKEN))
          .body("results", hasKey("expiry"))
          .body("results", hasKey("server"))
          .extract().path("results");
    }
    
    @Test
    @Order(5)
    @DisplayName("Cannot call get default creds after calling first time")
    void cannotCallDefault() {

      given().auth().oauth2(adminToken).contentType(ContentType.JSON).when()
          .get("/user/clientcredentials").then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_DEFAULT_CLIENT_EXISTS.toString()))
          .body("detail", equalTo(ERR_DETAIL_DEFAULT_CLIENT_EXISTS.toString()))
          .body("context", hasKey(RESP_CLIENT_ID))
          .body("context.clientId", equalTo(clientId));
    }
    
    @Test
    @Order(6)
    @DisplayName("Regenerate client - random client ID")
    void regenRandomClientId() {

      JsonObject body = new JsonObject().put("clientId", UUID.randomUUID());
      
      given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/user/clientcredentials").then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_CLI_ID.toString()))
          .body("detail", equalTo(ERR_DETAIL_INVALID_CLI_ID.toString()));
    }
    
    @Test
    @Order(7)
    @DisplayName("Regenerate client - client ID not owned by admin (owned by consumer)")
    void regenNotOwnedClientId() {

      String consumerClientIdclient = given().auth().oauth2(consumerToken).contentType(ContentType.JSON).when()
          .get("/user/clientcredentials").then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_CREATED_DEFAULT_CLIENT.toString()))
          .extract().path("results.clientId");
      
      JsonObject body = new JsonObject().put("clientId", consumerClientIdclient);
      
      given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/user/clientcredentials").then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_CLI_ID.toString()))
          .body("detail", equalTo(ERR_DETAIL_INVALID_CLI_ID.toString()));
    }
  }
}
