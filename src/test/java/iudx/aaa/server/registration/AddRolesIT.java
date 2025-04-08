package iudx.aaa.server.registration;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.admin.Constants.*;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.registration.Constants.*;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.models.RoleStatus;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.util.Urn;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
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

/**
 * Integration tests for adding consumer and provider roles. Also tests capabilities of user who has
 * provider role in pending state.
 */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class AddRolesIT {

  private static final String ALPHA_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String BRAVO_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String APD_URL =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static String tokenForInvalidTests;

  /** Token of admin of both servers + trustee. */
  private static String tokenRoles;

  private static String tokenRolesEmail = IntegTestHelpers.email();
  private static Map<String, String> tokenRolesClientCreds;

  @BeforeAll
  static void setup(KcAdminInt kc) {

    tokenForInvalidTests = kc.createUser(IntegTestHelpers.email());
    tokenRoles = kc.createUser(tokenRolesEmail);

    // create RSs
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", ALPHA_SERVER).put("owner", tokenRolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReq.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Alpha RS", is(201)));

    rsReq =
        new JsonObject().put("name", "name").put("url", BRAVO_SERVER).put("owner", tokenRolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReq.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Bravo RS", is(201)));

    // make APD to test trustee cannot search for pending provider
    JsonObject apdReq =
        new JsonObject().put("name", "name").put("url", APD_URL).put("owner", tokenRolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(apdReq.toString())
        .when()
        .post("/apd")
        .then()
        .statusCode(describedAs("Setup - Created dummy APD", is(201)));

    String clientInfo =
        given()
            .auth()
            .oauth2(tokenRoles)
            .contentType(ContentType.JSON)
            .when()
            .get("/user/clientcredentials")
            .then()
            .statusCode(201)
            .extract()
            .response()
            .asString();

    String rolesClientId =
        new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
    String rolesClientSecret =
        new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);

    tokenRolesClientCreds = Map.of("clientId", rolesClientId, "clientSecret", rolesClientSecret);
  }

  @Test
  @DisplayName("No token")
  void noToken() {
    JsonObject req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Missing Body")
  void missingBody() {

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Missing and invalid keys")
  void missingAndInvalidKeys() {
    JsonObject req = new JsonObject();

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), "hello")
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), "hello");

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put("phone", RandomStringUtils.random(10));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put("phone", "9889889881")
            .put("userInfo", RandomStringUtils.random(10));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // only 5 properties allowed in userInfo
    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put("phone", "9889889881")
            .put(
                "userInfo",
                new JsonObject()
                    .put("1", true)
                    .put("2", true)
                    .put("3", true)
                    .put("4", true)
                    .put("5", true)
                    .put("6", true));
  }

  @Test
  @DisplayName("Invalid consumer/provider arrays")
  void invalidConsProvArrays() {
    // empty consumer arr
    JsonObject req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray())
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // empty provider arr
    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray());

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // invalid URL in consumer
    req =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, "%%7381498.com")))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // invalid URL in provider
    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, "%%7381498.com")));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // duplicates in consumer
    req =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, ALPHA_SERVER)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // duplicates in provider
    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // uppercase in consumer
    req =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER.toUpperCase())))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // uppercase in provider
    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER.toUpperCase())));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("RS does not exist - consumer, provider cases")
  void nonExistentRs() {
    String nonExistentServer1 = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    String nonExistentServer2 = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    JsonObject req =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, nonExistentServer1)))
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NO_EXIST))
        .body("detail", equalTo(ERR_DETAIL_RS_NO_EXIST))
        .body("context." + ERR_CONTEXT_NOT_FOUND_RS_URLS, containsInAnyOrder(nonExistentServer1));

    req =
        new JsonObject()
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, nonExistentServer1)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NO_EXIST))
        .body("detail", equalTo(ERR_DETAIL_RS_NO_EXIST))
        .body("context." + ERR_CONTEXT_NOT_FOUND_RS_URLS, containsInAnyOrder(nonExistentServer1));

    req =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, nonExistentServer1)))
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(ALPHA_SERVER, nonExistentServer2)));

    given()
        .auth()
        .oauth2(tokenForInvalidTests)
        .contentType(ContentType.JSON)
        .body(req.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NO_EXIST))
        .body("detail", equalTo(ERR_DETAIL_RS_NO_EXIST))
        .body(
            "context." + ERR_CONTEXT_NOT_FOUND_RS_URLS,
            containsInAnyOrder(nonExistentServer1, nonExistentServer2));
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName("Consumer role adding tests")
  class AddingConsumerRoleTests {

    String aliceToken;
    String aliceEmail = IntegTestHelpers.email();
    String bobToken;
    String bobEmail = IntegTestHelpers.email();

    @BeforeAll
    void setup(KcAdminInt kc) {
      aliceToken = kc.createUser(aliceEmail);
      bobToken = kc.createUser(bobEmail);
    }

    JsonObject aliceUserInfo = new JsonObject().put("alice", true);
    String alicePhone = "9888999882";

    @Test
    @Order(1)
    @DisplayName("Add consumer role for Alpha sevrer with phone for Alice - success")
    void aliceConsumerSingleRsSuccess() {
      JsonObject req =
          new JsonObject()
              .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
              .put("phone", alicePhone)
              .put("userInfo", aliceUserInfo);

      given()
          .auth()
          .oauth2(aliceToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(aliceToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER))
          .body("results.email", equalTo(aliceEmail))
          .body("results.phone", equalTo(alicePhone))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("Cannot reg with same RS for Alice")
    void failRegAgain() {
      JsonObject req =
          new JsonObject()
              .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
              .put("phone", alicePhone)
              .put("userInfo", aliceUserInfo);

      given()
          .auth()
          .oauth2(aliceToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_FOR_RS_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_CONSUMER_FOR_RS_EXISTS))
          .body("context." + ERR_CONTEXT_EXISTING_ROLE_FOR_RS, containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(3)
    @DisplayName("Listing roles for Alice - has consumer role for Alpha")
    void listRolesAlice() {

      given()
          .auth()
          .oauth2(aliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(aliceToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER))
          .body("results.email", equalTo(aliceEmail))
          .body("results.phone", equalTo(alicePhone))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(4)
    @DisplayName(
        "Register for provider for Alpha, Bravo and consumer for both servers - "
            + "fails because already has consumer roles for Alpha")
    void regProviderAndConsumerFails() {

      JsonObject req =
          new JsonObject()
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)));

      given()
          .auth()
          .oauth2(aliceToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_FOR_RS_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_CONSUMER_FOR_RS_EXISTS))
          .body("context." + ERR_CONTEXT_EXISTING_ROLE_FOR_RS, containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(5)
    @DisplayName(
        "Alice register for provider roles for Alpha and Bravo and consumer role for Bravo")
    void aliceRegProviderBothServers() {

      JsonObject req =
          new JsonObject()
              .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(BRAVO_SERVER)))
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)));

      given()
          .auth()
          .oauth2(aliceToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(aliceToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(aliceEmail))
          .body("results.phone", equalTo(alicePhone))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(6)
    @DisplayName(
        "Listing roles for Alice - has consumer role for Alpha, Bravo - no provider since is pending")
    void listRolesAliceAfterProviderReg() {

      given()
          .auth()
          .oauth2(aliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(aliceToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(aliceEmail))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(7)
    @DisplayName("RS admin can see pending registration for Alpha, Bravo servers for Alice")
    void adminCanSeePendingReg() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(aliceEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(aliceEmail, ALPHA_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(aliceEmail, BRAVO_SERVER), hasKey("alice"))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(aliceEmail, ALPHA_SERVER), hasKey("alice"));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName("Provider role adding tests")
  class AddingProviderRoleTests {

    String bobToken;
    String bobEmail = IntegTestHelpers.email();

    @BeforeAll
    void setup(KcAdminInt kc) {
      bobToken = kc.createUser(bobEmail);
    }

    JsonObject bobUserInfo = new JsonObject().put("bob", true);
    String bobPhone = "9888999882";

    @Test
    @Order(1)
    @DisplayName("Add provider role for Alpha server with phone for Bob - success")
    void bobProviderSingleRsSuccess() {
      JsonObject req =
          new JsonObject()
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
              .put("phone", bobPhone)
              .put("userInfo", bobUserInfo);

      given()
          .auth()
          .oauth2(bobToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(bobToken)))
          .body("results.roles", hasSize(0))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.rolesToRsMapping.consumer", is(nullValue()))
          .body("results.email", equalTo(bobEmail))
          .body("results.phone", equalTo(bobPhone))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(2)
    @DisplayName("Cannot reg with same RS for Bob since reg is pending")
    void failRegAgain() {
      JsonObject req =
          new JsonObject()
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(ALPHA_SERVER)))
              .put("phone", bobPhone)
              .put("userInfo", bobUserInfo);

      given()
          .auth()
          .oauth2(bobToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("context.pending", containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(3)
    @DisplayName("Listing roles for Bob returns 404 since no approved roles")
    void listRolesBob() {

      given()
          .auth()
          .oauth2(bobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES));
    }

    @Test
    @Order(4)
    @DisplayName("RS admin can see pending registration for Alpha servers for Bob")
    void adminCanSeePendingRegForAlpha() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(bobEmail, ALPHA_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(bobEmail, ALPHA_SERVER), hasKey("bob"));
    }

    @Test
    @Order(5)
    @DisplayName(
        "Register for provider for Alpha, Bravo and consumer for both servers - "
            + "fails because already has pending provider roles for Alpha")
    void regProviderAndConsumerFails() {

      JsonObject req =
          new JsonObject()
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)));

      given()
          .auth()
          .oauth2(bobToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("context.pending", containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(6)
    @DisplayName(
        "Bob register for consumer roles for Alpha and Bravo and provider for Bravo "
            + "- tries to change userInfo and phone - phone unchanged")
    void bobRegConsumerBothServers() {

      String newPhone = "9009009001";

      JsonObject req =
          new JsonObject()
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(BRAVO_SERVER)))
              .put("userInfo", new JsonObject().put("something-else", true))
              .put("phone", newPhone);

      given()
          .auth()
          .oauth2(bobToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(bobToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(bobEmail))
          .body("results.phone", equalTo(bobPhone))
          .body("results.phone", not(equalTo(newPhone)))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(7)
    @DisplayName(
        "RS admin can see pending registration for Alpha, Bravo servers for Bob - userInfo unchanged")
    void adminCanSeePendingReg() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(bobEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(bobEmail, ALPHA_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(bobEmail, BRAVO_SERVER), hasKey("bob"))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(bobEmail, ALPHA_SERVER), hasKey("bob"))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(bobEmail, BRAVO_SERVER), not(hasKey("something-else")))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(bobEmail, ALPHA_SERVER), not(hasKey("something-else")));
    }

    @Test
    @Order(8)
    @DisplayName(
        "Listing roles for Bob - has consumer role for Alpha and Bravo - no provider since is pending state")
    void listRolesBobAfterProviderReg() {

      given()
          .auth()
          .oauth2(bobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(bobToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(bobEmail))
          .body("results.phone", equalTo(bobPhone))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName(
      "Adding roles to rejected and approved providers - but phone and userinfo cannot be updated")
  class AddingRoleToApprovedRejectedProvidersTests {

    String rejectedAlphaProvToken;
    String rejectedAlphaProvEmail = IntegTestHelpers.email();

    String approvedAlphaProvToken;
    String approvedAlphaProvEmail = IntegTestHelpers.email();

    @BeforeAll
    void setup(KcAdminInt kc) {
      rejectedAlphaProvToken = kc.createUser(rejectedAlphaProvEmail);
      approvedAlphaProvToken = kc.createUser(approvedAlphaProvEmail);

      JsonObject roleReq = new JsonObject().put("provider", new JsonArray().add(ALPHA_SERVER));

      given()
          .auth()
          .oauth2(rejectedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(roleReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(
              describedAs(
                  "Setup - Register provider (to be rejected) "
                      + "for Alpha RS Server - no phone, no userinfo",
                  is(200)));

      given()
          .auth()
          .oauth2(approvedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(roleReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(
              describedAs(
                  "Setup - Register provider (to be approved) "
                      + "for Alpha RS Server - no phone, no userinfo",
                  is(200)));

      Response response =
          given()
              .auth()
              .oauth2(tokenRoles)
              .contentType(ContentType.JSON)
              .when()
              .get("/admin/provider/registrations")
              .then()
              .statusCode(describedAs("Setup - Get provider reg. ID", is(200)))
              .extract()
              .response();

      String rejectingAlphaId =
          response
              .jsonPath()
              .param("email", rejectedAlphaProvEmail)
              .param("rsUrl", ALPHA_SERVER)
              .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");
      String approvingAlphaId =
          response
              .jsonPath()
              .param("email", approvedAlphaProvEmail)
              .param("rsUrl", ALPHA_SERVER)
              .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");

      JsonObject approveAndRejectReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase())
                              .put("id", rejectingAlphaId))
                      .add(
                          new JsonObject()
                              .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                              .put("id", approvingAlphaId)));

      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .body(approveAndRejectReq.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(
              describedAs("Setup - Approve and Reject providers on Alpha RS server", is(200)));
    }

    @Test
    @Order(1)
    @DisplayName(
        "Rejected provider on Alpha cannot register for all servers as provider and consumer, since rejected on Alpha")
    void rejProviderFailAllRolesAllServers() {
      JsonObject req =
          new JsonObject()
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)));

      given()
          .auth()
          .oauth2(rejectedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS))
          .body("context.rejected", containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(2)
    @DisplayName(
        "Rejected provider successfully registers for provider on Bravo server and consumer for both servers"
            + " - cannot update phone")
    void rejProviderSuccess() {
      JsonObject req =
          new JsonObject()
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(BRAVO_SERVER)))
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put("phone", "8998998992");

      given()
          .auth()
          .oauth2(rejectedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body(
              "results.userId",
              equalTo(IntegTestHelpers.getUserIdFromKcToken(rejectedAlphaProvToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(rejectedAlphaProvEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("Listing roles for rejected provider returns only consumer roles")
    void listRolesRejProvider() {

      given()
          .auth()
          .oauth2(rejectedAlphaProvToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body(
              "results.userId",
              equalTo(IntegTestHelpers.getUserIdFromKcToken(rejectedAlphaProvToken)))
          .body("results.roles", containsInAnyOrder(Roles.CONSUMER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", is(nullValue()))
          .body("results.email", equalTo(rejectedAlphaProvEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("RS admin can see pending registration for Bravo servers for Rej Provider")
    void adminCanSeePendingRegForBravo() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(rejectedAlphaProvEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(rejectedAlphaProvEmail, ALPHA_SERVER), is(nullValue()));
    }

    @Test
    @Order(5)
    @DisplayName(
        "Approved provider on Alpha cannot register as provider for all servers, since approved on Alpha")
    void approvedProviderAllServerFails() {
      JsonObject req =
          new JsonObject()
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)));

      given()
          .auth()
          .oauth2(approvedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_FOR_RS_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_PROVIDER_FOR_RS_EXISTS))
          .body("context." + ERR_CONTEXT_EXISTING_ROLE_FOR_RS, containsInAnyOrder(ALPHA_SERVER));
    }

    @Test
    @Order(6)
    @DisplayName(
        "Approved provider successfully registers for provider on Bravo server and consumer for both servers"
            + " - cannot update phone, try updating userinfo from empty")
    void approvedProviderSuccess() {
      JsonObject req =
          new JsonObject()
              .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(BRAVO_SERVER)))
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put("phone", "8998998992")
              .put("userInfo", new JsonObject().put("approved", true));

      given()
          .auth()
          .oauth2(approvedAlphaProvToken)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body(
              "results.userId",
              equalTo(IntegTestHelpers.getUserIdFromKcToken(approvedAlphaProvToken)))
          .body(
              "results.roles",
              containsInAnyOrder(
                  Roles.CONSUMER.toString().toLowerCase(), Roles.PROVIDER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", containsInAnyOrder(ALPHA_SERVER))
          .body("results.email", equalTo(approvedAlphaProvEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(7)
    @DisplayName(
        "Listing roles for approved provider - has consumer role for Alpha and Bravo - and provider role for Alpha")
    void listRolesApprovedProvider() {

      given()
          .auth()
          .oauth2(approvedAlphaProvToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body(
              "results.userId",
              equalTo(IntegTestHelpers.getUserIdFromKcToken(approvedAlphaProvToken)))
          .body(
              "results.roles",
              containsInAnyOrder(
                  Roles.CONSUMER.toString().toLowerCase(), Roles.PROVIDER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.provider", containsInAnyOrder(ALPHA_SERVER))
          .body("results.email", equalTo(approvedAlphaProvEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", is(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(8)
    @DisplayName(
        "RS admin can see pending registration for Bravo for approved provider - userinfo empty")
    void adminCanSeePendingRegForBravoForApprovedProv() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(approvedAlphaProvEmail, ALPHA_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(approvedAlphaProvEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(approvedAlphaProvEmail, BRAVO_SERVER), not(hasKey("approved")));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Testing capabilities of provider in pending state")
  class PendingProviderScenarios {
    String providerEmail = IntegTestHelpers.email();
    String providerToken;

    @BeforeAll
    void setup(KcAdminInt kc) {
      providerToken = kc.createUser(providerEmail);

      JsonObject provReq = new JsonObject().put("provider", new JsonArray(List.of(ALPHA_SERVER)));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(provReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(describedAs("Setup - Added provider reg for pending test", is(200)));
    }

    @Test
    @DisplayName("Pending provider doesn't have provider role")
    void listingRolesFail() {

      given()
          .auth()
          .oauth2(providerToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
    }

    @Test
    @DisplayName("Pending provider cannot get/regen client creds")
    void gettingCredsFail() {

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/clientcredentials")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));

      JsonObject body = new JsonObject().put("clientId", UUID.randomUUID().toString());

      given()
          .auth()
          .oauth2(providerToken)
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

    @Test
    @DisplayName("Pending provider cannot do any delegation operations")
    void delegationsFail() {

      given()
          .auth()
          .oauth2(providerToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", "email@email.com")
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_CREATE_DELEGATE_ROLES));

      JsonObject deleteDelegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString())));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(deleteDelegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_DEL_DELEGATE_ROLES));
    }

    @Test
    @DisplayName("Pending provider cannot get provider token")
    void tokenFail() {

      JsonObject tokenBody =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
    }

    @Test
    @DisplayName("Pending provider cannot be searched by trustee")
    void trusteeSearchFail() {

      Map<String, String> queryParamsEmail =
          Map.of(
              "email",
              providerEmail,
              "resourceServer",
              ALPHA_SERVER,
              "role",
              Roles.PROVIDER.toString().toLowerCase());

      given()
          .when()
          .headers(tokenRolesClientCreds)
          .queryParams(queryParamsEmail)
          .get("/user/search")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName(
      "Users having other roles (admin, trustee) adding consumer/provider roles - but phone or userinfo cannot be updated")
  class OtherRolesTests {

    @Test
    @Order(1)
    @DisplayName(
        "Add consumer and provider role for Alpha, Bravo servers with phone for tokenRoles user "
            + "- cannot update phone or userinfo (and repeat fails). We also see client credentials in response")
    void tokenRolesAddingRolesSuccess() {

      JsonObject req =
          new JsonObject()
              .put(
                  Roles.CONSUMER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put(
                  Roles.PROVIDER.toString().toLowerCase(),
                  new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER)))
              .put("phone", "9009009001")
              .put("userInfo", new JsonObject().put("otherRoles", true));

      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(tokenRoles)))
          .body(
              "results.roles",
              containsInAnyOrder(
                  Roles.CONSUMER.toString().toLowerCase(),
                  Roles.ADMIN.toString().toLowerCase(),
                  Roles.TRUSTEE.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.admin", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.trustee", containsInAnyOrder(APD_URL))
          .body("results.email", equalTo(tokenRolesEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", hasSize(1))
          .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
          .body("results.clients[0].clientId", not(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));

      // repeat fails
      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .body(req.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_FOR_RS_EXISTS))
          .body("detail", equalTo(ERR_DETAIL_CONSUMER_FOR_RS_EXISTS))
          .body(
              "context." + ERR_CONTEXT_EXISTING_ROLE_FOR_RS,
              containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER));
    }

    @Test
    @Order(2)
    @DisplayName("Listing roles for tokenRoles - has all except provider since pending")
    void listRoles() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(tokenRoles)))
          .body(
              "results.roles",
              containsInAnyOrder(
                  Roles.CONSUMER.toString().toLowerCase(),
                  Roles.ADMIN.toString().toLowerCase(),
                  Roles.TRUSTEE.toString().toLowerCase()))
          .body("results.rolesToRsMapping.consumer", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.admin", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body("results.rolesToRsMapping.trustee", containsInAnyOrder(APD_URL))
          .body("results.email", equalTo(tokenRolesEmail))
          .body("results.phone", is(nullValue()))
          .body("results.clients", hasSize(1))
          .body("results.clients[0].clientName", equalTo(DEFAULT_CLIENT))
          .body("results.clients[0].clientId", not(nullValue()))
          .body("results.name.firstName", not(nullValue()))
          .body("results.name.lastName", not(nullValue()));
    }

    @Test
    @Order(3)
    @DisplayName(
        "RS admin can see pending registration for Alpha and Bravo for themself - userinfo not updated")
    void adminCanSeePendingRegForAlphaAndBravo() {

      given()
          .auth()
          .oauth2(tokenRoles)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(tokenRolesEmail, ALPHA_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }",
              withArgs(tokenRolesEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(tokenRolesEmail, ALPHA_SERVER), not(hasKey("otherRoles")))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s' }.userInfo",
              withArgs(tokenRolesEmail, BRAVO_SERVER), not(hasKey("otherRoles")));
    }
  }
}
