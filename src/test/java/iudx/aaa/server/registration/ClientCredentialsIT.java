package iudx.aaa.server.registration;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.registration.Constants.*;
import static iudx.aaa.server.token.Constants.*;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.util.Urn;
import java.util.UUID;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
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

/** Integration tests for getting default client credentials and regenerating client credentials. */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class ClientCredentialsIT {

  /** Token with admin role. */
  private static String adminToken;

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static String tokenNoRoles;

  @BeforeAll
  static void setup(KcAdminInt kc) {
    tokenNoRoles = kc.createUser(IntegTestHelpers.email());

    String adminEmail = IntegTestHelpers.email();
    adminToken = kc.createUser(adminEmail);

    // create RS
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER).put("owner", adminEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReq.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));
  }

  @Nested
  @DisplayName("User with no roles cannot create or reset client creds")
  class noRolesCannotCall {

    @Test
    @DisplayName("Cannot get default creds")
    void cannotDefault() {

      given()
          .auth()
          .oauth2(tokenNoRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/clientcredentials")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
    }

    @Test
    @DisplayName("Cannot call regen")
    void cannotRegen() {
      JsonObject body = new JsonObject().put("clientId", UUID.randomUUID().toString());

      given()
          .auth()
          .oauth2(tokenNoRoles)
          .contentType(ContentType.JSON)
          .body(body.toString())
          .when()
          .put("/user/clientcredentials")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
    }
  }

  @Test
  @DisplayName("Regenerate client secret - missing body")
  void regenMissingBody() {
    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .when()
        .put("/user/clientcredentials")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Regenerate client secret - missing clientId key")
  void regenMissingKey() {
    JsonObject body = new JsonObject();

    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .put("/user/clientcredentials")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Regenerate client secret - no token")
  void regenMissingToken() {
    JsonObject body = new JsonObject().put("clientId", UUID.randomUUID().toString());

    given()
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .put("/user/clientcredentials")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Get default client secret - no token")
  void defaultCliMissingToken() {

    given()
        .contentType(ContentType.JSON)
        .when()
        .get("/user/clientcredentials")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Get Default creds for COS Admin - will get inserted into DB")
  void getDefaultCredsForCosAdmin(KcAdminInt kc) {

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .when()
        .get("/user/clientcredentials")
        .then()
        .statusCode(201)
        .body("type", equalTo(Urn.URN_SUCCESS.toString()))
        .body("title", equalTo(SUCC_TITLE_CREATED_DEFAULT_CLIENT.toString()))
        .body("results.clientName", equalTo(DEFAULT_CLIENT))
        .body("results", hasKey(RESP_CLIENT_ID))
        .body("results", hasKey(RESP_CLIENT_SC))
        .extract()
        .response()
        .asString();
  }

  @Nested
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Test client credentials flow using the admin")
  class ClientCredentialsFlowTests {

    String clientId;
    String clientSecret;
    String newClientSecret;

    JsonObject tokenBody =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    String consumerToken;
    String consumerClientId;
    String consumerClientSecret;

    @BeforeAll
    void setup(KcAdminInt kc) {
      consumerToken = kc.createUser(IntegTestHelpers.email());

      // create consumer roles
      JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(consReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(describedAs("Setup - Added consumer", is(200)));

      String consClientInfo =
          given()
              .auth()
              .oauth2(consumerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/user/clientcredentials")
              .then()
              .statusCode(describedAs("Get creds for test consumer", is(201)))
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_CREATED_DEFAULT_CLIENT.toString()))
              .extract()
              .response()
              .asString();

      consumerClientId =
          new JsonObject(consClientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
      consumerClientSecret =
          new JsonObject(consClientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);
    }

    @Test
    @Order(1)
    @DisplayName("List user roles has no client array in it")
    void listUserNoClientCreds() {
      given()
          .auth()
          .oauth2(adminToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.clients", is(nullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("Add role has no client array in response")
    void addRoleNoClientCreds() {
      JsonObject req =
          new JsonObject()
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray().add(DUMMY_SERVER));

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("results.clients", is(nullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("Get Default creds")
    void getDefault() {

      String clientInfo =
          given()
              .auth()
              .oauth2(adminToken)
              .when()
              .get("/user/clientcredentials")
              .then()
              .statusCode(201)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_CREATED_DEFAULT_CLIENT.toString()))
              .body("results.clientName", equalTo(DEFAULT_CLIENT))
              .body("results", hasKey(RESP_CLIENT_ID))
              .body("results", hasKey(RESP_CLIENT_SC))
              .extract()
              .response()
              .asString();

      clientId = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
      clientSecret = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);
    }

    @Test
    @Order(4)
    @DisplayName("List roles now has client array")
    void listRolesHasClients() {

      given()
          .auth()
          .oauth2(adminToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.clients", hasSize(1))
          .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
          .body("results.clients[0].clientId", equalTo(clientId))
          .body("results.clients[0].clientSecret", is(nullValue()));
    }

    @Test
    @Order(5)
    @DisplayName("Adding role now has client creds in response")
    void addRoleHasClients() {

      JsonObject req =
          new JsonObject()
              .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray().add(DUMMY_SERVER));

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("results.clients", hasSize(1))
          .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
          .body("results.clients[0].clientId", equalTo(clientId))
          .body("results.clients[0].clientSecret", is(nullValue()));
    }

    @Test
    @Order(6)
    @DisplayName("Get admin identity token using creds")
    void callTokenApi() {

      given()
          .header("clientId", clientId)
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(TOKEN_SUCCESS.toString()))
          .body("results", hasKey(ACCESS_TOKEN))
          .body("results", hasKey("expiry"))
          .body("results", hasKey("server"));
    }

    @Test
    @Order(7)
    @DisplayName("Call regenerate client secret")
    void cannotRegen() {
      JsonObject body = new JsonObject().put("clientId", clientId);

      newClientSecret =
          given()
              .auth()
              .oauth2(adminToken)
              .contentType(ContentType.JSON)
              .body(body.toString())
              .when()
              .put("/user/clientcredentials")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_REGEN_CLIENT_SECRET.toString()))
              .body("results", hasKey(RESP_CLIENT_ARR))
              .body("results.clients", hasSize(1))
              .body("results.clients[0].clientId", equalTo(clientId))
              .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
              .body("results.clients[0].clientSecret", not(equalTo(clientSecret)))
              .extract()
              .path("results.clients[0].clientSecret");
    }

    @Test
    @Order(8)
    @DisplayName("Old Creds don't work, new creds do")
    void oldCredsNotWork() {

      given()
          .header("clientId", clientId)
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC.toString()))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC.toString()));

      given()
          .header("clientId", clientId)
          .header("clientSecret", newClientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(TOKEN_SUCCESS.toString()))
          .body("results", hasKey(ACCESS_TOKEN))
          .body("results", hasKey("expiry"))
          .body("results", hasKey("server"))
          .extract()
          .path("results");
    }

    @Test
    @Order(9)
    @DisplayName("Cannot call get default creds after calling first time")
    void cannotCallDefault() {

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/clientcredentials")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_DEFAULT_CLIENT_EXISTS.toString()))
          .body("detail", equalTo(ERR_DETAIL_DEFAULT_CLIENT_EXISTS.toString()))
          .body("context", hasKey(RESP_CLIENT_ID))
          .body("context.clientId", equalTo(clientId));
    }

    @Test
    @Order(10)
    @DisplayName("Regenerate client - random client ID")
    void regenRandomClientId() {

      JsonObject body = new JsonObject().put("clientId", UUID.randomUUID());

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(body.toString())
          .when()
          .put("/user/clientcredentials")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_CLI_ID.toString()))
          .body("detail", equalTo(ERR_DETAIL_INVALID_CLI_ID.toString()));
    }

    @Test
    @Order(11)
    @DisplayName("Regenerate client - client ID not owned by admin (owned by consumer)")
    void regenNotOwnedClientId() {

      JsonObject body = new JsonObject().put("clientId", consumerClientId);

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(body.toString())
          .when()
          .put("/user/clientcredentials")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_CLI_ID.toString()))
          .body("detail", equalTo(ERR_DETAIL_INVALID_CLI_ID.toString()));
    }

    @Test
    @Order(12)
    @DisplayName(
        "Client authorization - Invalid tests - mixing client ID and secret between 2 users")
    void mixingClientIdClientSecret() {

      // using consumer client ID w/ admin client secret
      given()
          .header("clientId", consumerClientId)
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC));

      // using admin client ID with consumer client secret
      given()
          .header("clientId", clientId)
          .header("clientSecret", consumerClientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC));
    }

    @Test
    @Order(13)
    @DisplayName("Client authorization - Invalid tests - client ID, client secret")
    void invalidClientIdClientSecret() {
      given()
          .header("clientId", RandomStringUtils.random(10))
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

      given()
          .header("clientId", clientId)
          .header("clientSecret", RandomStringUtils.random(10))
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }

    @Test
    @Order(14)
    @DisplayName("Client authorization tests - random client ID, client secret")
    void randomClientIdClientSecret() {
      given()
          .header("clientId", UUID.randomUUID().toString())
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC));

      given()
          .header("clientId", clientId)
          .header("clientSecret", Hex.encodeHexString(RandomUtils.nextBytes(CLIENT_SECRET_BYTES)))
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_CLIENT_ID_SEC))
          .body("detail", equalTo(INVALID_CLIENT_ID_SEC));
    }

    @Test
    @Order(15)
    @DisplayName("Client authorization tests - missing client ID, client secret")
    void missingClientIdClientSecret() {
      given()
          .header("clientSecret", clientSecret)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));

      given()
          .header("clientId", clientId)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
    }
  }
}
