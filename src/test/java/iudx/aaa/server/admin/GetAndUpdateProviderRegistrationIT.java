package iudx.aaa.server.admin;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.admin.Constants.*;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.registration.Constants.*;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.models.RoleStatus;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.registration.IntegTestHelpers;
import iudx.aaa.server.registration.KcAdminExtension;
import iudx.aaa.server.registration.KcAdminInt;
import iudx.aaa.server.registration.RestAssuredConfigExtension;
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
 * Integration tests for getting and updating provider registrations. Also tests the capabilities of
 * a rejected provider.
 */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class GetAndUpdateProviderRegistrationIT {

  /** User has provider (for ALPHA_SERVER), consumer, delegate and trustee role */
  private static String tokenRoles;

  private static String tokenNoRoles;
  private static String tokenAdminAlpha;
  private static String tokenAdminBravoCharlie;

  private static String ALPHA_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static String BRAVO_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static String CHARLIE_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

  @BeforeAll
  static void setup(KcAdminInt kc) {
    String rolesEmail = IntegTestHelpers.email();
    String adminAlphaEmail = IntegTestHelpers.email();
    String adminBravoCharlie = IntegTestHelpers.email();

    tokenRoles = kc.createUser(rolesEmail);
    tokenNoRoles = kc.createUser(IntegTestHelpers.email());
    tokenAdminAlpha = kc.createUser(adminAlphaEmail);
    tokenAdminBravoCharlie = kc.createUser(adminBravoCharlie);

    // create Alpha, Bravo and Charlie RSs
    JsonObject rsReqAlpha =
        new JsonObject().put("name", "name").put("url", ALPHA_SERVER).put("owner", adminAlphaEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqAlpha.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Alpha RS server", is(201)));

    JsonObject rsReqBravo =
        new JsonObject()
            .put("name", "name")
            .put("url", BRAVO_SERVER)
            .put("owner", adminBravoCharlie);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqBravo.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Bravo RS server", is(201)));

    JsonObject rsReqCharlie =
        new JsonObject()
            .put("name", "name")
            .put("url", CHARLIE_SERVER)
            .put("owner", adminBravoCharlie);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqCharlie.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Charlie RS server", is(201)));

    // create consumer, provider roles for Alpha server only
    JsonObject consReq =
        new JsonObject()
            .put("consumer", new JsonArray().add(ALPHA_SERVER))
            .put("provider", new JsonArray().add(ALPHA_SERVER));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(consReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(
            describedAs("Setup - Added consumer, provider roles for Alpha RS Server", is(200)));

    String providerRegId =
        given()
            .auth()
            .oauth2(tokenAdminAlpha)
            .contentType(ContentType.JSON)
            .when()
            .get("/admin/provider/registrations")
            .then()
            .statusCode(describedAs("Setup - Get provider reg. ID", is(200)))
            .extract()
            .path("results[0].id");

    JsonObject approveReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                            .put("id", providerRegId)));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(approveReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(describedAs("Setup - Approve provider on Alpha RS server", is(200)));

    // create delegation
    String delegatorEmail = IntegTestHelpers.email();
    String delegatorTok = kc.createUser(delegatorEmail);

    JsonObject delegatorConsReq =
        new JsonObject().put("consumer", new JsonArray().add(ALPHA_SERVER));

    given()
        .auth()
        .oauth2(delegatorTok)
        .contentType(ContentType.JSON)
        .body(delegatorConsReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added consumer delegaTOR", is(200)));

    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", rolesEmail)
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(delegatorTok)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(describedAs("Setup - Created delegation", is(201)));

    // create APD
    JsonObject apdReq =
        new JsonObject()
            .put("name", "name")
            .put("url", "apd" + RandomStringUtils.randomAlphabetic(10) + ".com")
            .put("owner", rolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(apdReq.toString())
        .when()
        .post("/apd")
        .then()
        .statusCode(describedAs("Setup - Created dummy APD", is(201)));
  }

  @Test
  @DisplayName("Get Provider Reg - No Token")
  void getProvNoToken() {
    given()
        .when()
        .get("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName(
      "Get Provider Reg - Consumer, provider, delegate, trustee, COS admin cannot call API")
  void getProvRolesCannotCall(KcAdminInt kc) {
    given()
        .auth()
        .oauth2(tokenRoles)
        .when()
        .get("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .when()
        .get("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));
  }

  @Test
  @DisplayName("Get Provider Reg - No roles cannot call API")
  void getProvRolesNoRolesCannotCall() {
    given()
        .auth()
        .oauth2(tokenNoRoles)
        .when()
        .get("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));
  }

  @Test
  @DisplayName("Get Provider Reg - Invalid filter val")
  void getProvInvalidFilter() {
    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .queryParam("filter", "sdawnejdjandqw")
        .when()
        .get("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Update Provider Reg - No Token")
  void updateProvNoToken() {
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .when()
        .body(request.toString())
        .put("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Update Provider Reg - Consumer, provider, trustee, delegate, COS admin cannot call")
  void updateProvRolesCannotCall(KcAdminInt kc) {
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));
  }

  @Test
  @DisplayName("Update Provider Reg - No roles cannot call")
  void updateProvNoRolesCannotCall() {
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
        .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));
  }

  @Test
  @DisplayName("Update Provider Reg - Missing body")
  void updateProvRolesMissingBody() {
    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .body(new JsonObject().toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Update Provider Reg - Invalid and missing keys")
  void updateProvInvalidBody() {
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    request =
        new JsonObject()
            .put("id", UUID.randomUUID().toString())
            .put("status", RoleStatus.APPROVED.toString().toLowerCase());

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", RandomStringUtils.random(10))
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.PENDING.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", "1234")));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Update Provider Reg - Duplicates")
  void updateProvRolesDuplicates() {
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Update Provider Reg - Duplicate IDs, different statuses")
  void updateProvRolesDuplicateIdsWithStatuses() {
    String badId = UUID.randomUUID().toString();
    JsonObject request =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("id", UUID.randomUUID().toString())
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("id", badId)
                            .put("status", RoleStatus.REJECTED.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("id", badId)
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenAdminAlpha)
        .contentType(ContentType.JSON)
        .body(request.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_DUPLICATE_REQ))
        .body("detail", equalTo(badId));
  }

  @Nested
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Testing Get and Update provider registrations")
  class GetAndUpdateProviderRegTestTogether {
    String providerAliceEmail = IntegTestHelpers.email();
    String providerBobEmail = IntegTestHelpers.email();

    String aliceToken;
    String bobToken;

    @BeforeAll
    void setup(KcAdminInt kc) {
      aliceToken = kc.createUser(providerAliceEmail);
      bobToken = kc.createUser(providerBobEmail);

      JsonObject provReq =
          new JsonObject()
              .put("provider", new JsonArray(List.of(ALPHA_SERVER, BRAVO_SERVER, CHARLIE_SERVER)));

      given()
          .auth()
          .oauth2(aliceToken)
          .contentType(ContentType.JSON)
          .body(provReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(
              describedAs(
                  "Setup - Added provider reg for providerAlice for all RS Server", is(200)));

      given()
          .auth()
          .oauth2(bobToken)
          .contentType(ContentType.JSON)
          .body(provReq.toString())
          .when()
          .post("/user/roles")
          .then()
          .statusCode(
              describedAs("Setup - Added provider reg for providerBob for all RS Server", is(200)));
    }

    @Test
    @Order(1)
    @DisplayName("Pending providers have no roles - no provider role especially")
    void pendingProvidersHaveNoRoles() {

      given()
          .auth()
          .oauth2(aliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("results", is(nullValue()));

      given()
          .auth()
          .oauth2(bobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("results", is(nullValue()));
    }

    @Test
    @Order(2)
    @DisplayName(
        "Checking pending for Alpha admin and Bravo+Charlie admin - will have entries for Alice and Bob providers")
    void getPending() {
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.status", everyItem(equalTo(RoleStatus.PENDING.toString().toLowerCase())))
          .body("results.rsUrl", everyItem(equalTo(ALPHA_SERVER)))
          .body("results.name", everyItem(hasKey("firstName")))
          .body("results.name", everyItem(hasKey("lastName")))
          .body("results", everyItem(hasKey("userInfo")))
          .body("results", everyItem(hasKey("userId")))
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), not(nullValue()))
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), not(nullValue()));

      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .when()
          .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.status", everyItem(equalTo(RoleStatus.PENDING.toString().toLowerCase())))
          .body("results.rsUrl", everyItem(oneOf(BRAVO_SERVER, CHARLIE_SERVER)))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, CHARLIE_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, CHARLIE_SERVER), not(nullValue()));
    }

    @Test
    @Order(3)
    @DisplayName(
        "Checking approved for Alpha admin and Bravo+Charlie admin - will have NO entries for Alice and Bob providers")
    void getApproved() {
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.APPROVED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), nullValue());

      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .when()
          .queryParam("filter", RoleStatus.APPROVED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.find { it.email == '%s' }", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s' }", withArgs(providerBobEmail), nullValue());
    }

    @Test
    @Order(4)
    @DisplayName(
        "Checking rejected for Alpha admin and Bravo+Charlie admin - will have NO entries for Alice and Bob providers")
    void getRejected() {
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.REJECTED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), nullValue());

      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .when()
          .queryParam("filter", RoleStatus.REJECTED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.find { it.email == '%s' }", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s' }", withArgs(providerBobEmail), nullValue());
    }

    @Test
    @Order(5)
    @DisplayName(
        "Bravo+Charlie admin tries to approve Alice for Alpha server - fails, does not own ID")
    void adminDoesNotOwnProviderRegId() {
      String aliceAlphaRegId =
          given()
              .auth()
              .oauth2(tokenAdminAlpha)
              .when()
              .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
              .get("/admin/provider/registrations")
              .then()
              .statusCode(200)
              .extract()
              .path("results.find { it.email == '%s'}.id", providerAliceEmail);

      JsonObject request =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("id", aliceAlphaRegId)
                              .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_PROV_REG_ID))
          .body("detail", equalTo(aliceAlphaRegId));
    }

    @Test
    @Order(6)
    @DisplayName("Alice approved for Alpha server success - repeat fails")
    void approveSingleSuccessRepeatFails() {
      String aliceAlphaRegId =
          given()
              .auth()
              .oauth2(tokenAdminAlpha)
              .when()
              .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
              .get("/admin/provider/registrations")
              .then()
              .statusCode(200)
              .extract()
              .path("results.find { it.email == '%s'}.id", providerAliceEmail);

      JsonObject request =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("id", aliceAlphaRegId)
                              .put("status", RoleStatus.APPROVED.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROV_STATUS_UPDATE))
          .body("results", hasSize(1))
          .body("results[0]", hasKey("userInfo"))
          .body("results[0]", hasKey("userId"))
          .body("results[0].name", hasKey("firstName"))
          .body("results[0].name", hasKey("lastName"))
          .body("results[0].email", equalTo(providerAliceEmail))
          .body("results[0].rsUrl", equalTo(ALPHA_SERVER));

      // repeating
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_PROV_REG_ID))
          .body("detail", equalTo(aliceAlphaRegId));
    }

    @Test
    @Order(7)
    @DisplayName(
        "Alpha admin checks pending, rejected and approved - Alice in approved, Bob still pending")
    void checkGetAfterApproved() {
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), not(nullValue()));

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.REJECTED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), nullValue())
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), nullValue());

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.APPROVED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.status", everyItem(equalTo(RoleStatus.APPROVED.toString().toLowerCase())))
          .body("results.rsUrl", everyItem(equalTo(ALPHA_SERVER)))
          .body("results.name", everyItem(hasKey("firstName")))
          .body("results.name", everyItem(hasKey("lastName")))
          .body("results", everyItem(hasKey("userInfo")))
          .body("results", everyItem(hasKey("userId")))
          .body("results.find { it.email == '%s'}", withArgs(providerAliceEmail), not(nullValue()))
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), nullValue());
    }

    @Test
    @Order(8)
    @DisplayName(
        "Alpha provider tries reject Bob w/ invalid ID in request - fails - Bob is not rejected")
    void rejectedMultipleInvalidId() {

      String bobAlphaRegId =
          given()
              .auth()
              .oauth2(tokenAdminAlpha)
              .when()
              .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
              .get("/admin/provider/registrations")
              .then()
              .statusCode(200)
              .extract()
              .path("results.find { it.email == '%s'}.id", providerBobEmail);

      String randId = UUID.randomUUID().toString();

      JsonObject request =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("id", bobAlphaRegId)
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("id", randId)
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_PROV_REG_ID))
          .body("detail", equalTo(randId));

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.REJECTED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.find { it.email == '%s'}", withArgs(providerBobEmail), nullValue());
    }

    @Test
    @Order(9)
    @DisplayName(
        "Bravo+Charlie approves Alice, Bob for Bravo, rejects Alice, Bob for Charlie - repeat fails")
    void rejectApproveMultipleRequestsMultipleServers() {
      Response resp =
          given()
              .auth()
              .oauth2(tokenAdminBravoCharlie)
              .when()
              .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
              .get("/admin/provider/registrations")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
              .extract()
              .response();

      JsonPath jpath = resp.jsonPath();

      String aliceBravoRegId =
          jpath
              .param("email", providerAliceEmail)
              .param("url", BRAVO_SERVER)
              .get("results.find { it.email == email && it.rsUrl == url }.id");
      String bobBravoRegId =
          jpath
              .param("email", providerBobEmail)
              .param("url", BRAVO_SERVER)
              .get("results.find { it.email == email && it.rsUrl == url }.id");
      String aliceCharlieRegId =
          jpath
              .param("email", providerAliceEmail)
              .param("url", CHARLIE_SERVER)
              .get("results.find { it.email == email && it.rsUrl == url }.id");
      String bobCharlieRegId =
          jpath
              .param("email", providerBobEmail)
              .param("url", CHARLIE_SERVER)
              .get("results.find { it.email == email && it.rsUrl == url }.id");

      JsonObject request =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("id", aliceBravoRegId)
                              .put("status", RoleStatus.APPROVED.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("id", bobBravoRegId)
                              .put("status", RoleStatus.APPROVED.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("id", aliceCharlieRegId)
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("id", bobCharlieRegId)
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROV_STATUS_UPDATE))
          .body("results", hasSize(4))
          .body("results", everyItem(hasKey("userInfo")))
          .body("results", everyItem(hasKey("userId")))
          .body("results.name", everyItem(hasKey("firstName")))
          .body("results.name", everyItem(hasKey("lastName")))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}.status",
              withArgs(providerAliceEmail, BRAVO_SERVER),
              equalTo(RoleStatus.APPROVED.toString().toLowerCase()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}.status",
              withArgs(providerBobEmail, BRAVO_SERVER),
              equalTo(RoleStatus.APPROVED.toString().toLowerCase()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}.status",
              withArgs(providerAliceEmail, CHARLIE_SERVER),
              equalTo(RoleStatus.REJECTED.toString().toLowerCase()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}.status",
              withArgs(providerBobEmail, CHARLIE_SERVER),
              equalTo(RoleStatus.REJECTED.toString().toLowerCase()));

      // repeating
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_PROV_REG_ID));
    }

    @Test
    @Order(10)
    @DisplayName(
        "Bravo+Charlie admin checks pending, rejected and approved - Both providers in approved for Bravo, in rejected for Charlie")
    void checkGetAfterApprovedReject() {
      // none of the providers in pending anymore
      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .when()
          .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, BRAVO_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, BRAVO_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, CHARLIE_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, CHARLIE_SERVER), nullValue());

      // will be in approved for BRAVO SERVER only
      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .when()
          .queryParam("filter", RoleStatus.APPROVED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("results.status", everyItem(equalTo(RoleStatus.APPROVED.toString().toLowerCase())))
          .body("results.name", everyItem(hasKey("firstName")))
          .body("results.name", everyItem(hasKey("lastName")))
          .body("results", everyItem(hasKey("userInfo")))
          .body("results", everyItem(hasKey("userId")))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, BRAVO_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, CHARLIE_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, CHARLIE_SERVER), nullValue());

      // will be in rejected for CHARLIE SERVER only
      given()
          .auth()
          .oauth2(tokenAdminBravoCharlie)
          .when()
          .queryParam("filter", RoleStatus.REJECTED.toString().toLowerCase())
          .get("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_PROVIDER_REGS))
          .body("results.status", everyItem(equalTo(RoleStatus.REJECTED.toString().toLowerCase())))
          .body("results.name", everyItem(hasKey("firstName")))
          .body("results.name", everyItem(hasKey("lastName")))
          .body("results", everyItem(hasKey("userInfo")))
          .body("results", everyItem(hasKey("userId")))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, BRAVO_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, BRAVO_SERVER), nullValue())
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerAliceEmail, CHARLIE_SERVER), not(nullValue()))
          .body(
              "results.find { it.email == '%s' && it.rsUrl == '%s'}",
              withArgs(providerBobEmail, CHARLIE_SERVER), not(nullValue()));
    }

    @Test
    @Order(11)
    @DisplayName(
        "List roles after provider approve/reject - Alice has provider role for Alpha and Bravo. Bob has only for Bravo")
    void listingRolesAfterProvidersStatusChange() {

      given()
          .auth()
          .oauth2(aliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.roles", hasItem(Roles.PROVIDER.toString().toLowerCase()))
          .body(
              "results.rolesToRsMapping.provider", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER));

      given()
          .auth()
          .oauth2(bobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.roles", hasItem(Roles.PROVIDER.toString().toLowerCase()))
          .body("results.rolesToRsMapping.provider", containsInAnyOrder(BRAVO_SERVER));
    }
  }

  // create provider for ALPHA server, reject and test conditions
  // get client creds for tokenRoles to test search user API
  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Testing capabilities of provider in rejected state")
  class RejectedProviderScenarios {
    String providerEmail = IntegTestHelpers.email();
    String providerToken;
    Map<String, String> rolesTrusteeClientCreds;

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
          .statusCode(describedAs("Setup - Added provider reg for rejection test", is(200)));

      String regId =
          given()
              .auth()
              .oauth2(tokenAdminAlpha)
              .when()
              .queryParam("filter", RoleStatus.PENDING.toString().toLowerCase())
              .get("/admin/provider/registrations")
              .then()
              .statusCode(200)
              .extract()
              .path("results.find { it.email == '%s'}.id", providerEmail);

      JsonObject request =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("id", regId)
                              .put("status", RoleStatus.REJECTED.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(tokenAdminAlpha)
          .contentType(ContentType.JSON)
          .body(request.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

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

      rolesTrusteeClientCreds =
          Map.of("clientId", rolesClientId, "clientSecret", rolesClientSecret);
    }

    @Test
    @DisplayName("Rejected provider doesn't have provider role")
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
    @DisplayName("Rejected provider cannot get/regen client creds")
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
    @DisplayName("Rejected provider cannot do any delegation operations")
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
    @DisplayName("Rejected provider cannot get provider token")
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
    @DisplayName("Rejected provider cannot be searched by trustee")
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
          .headers(rolesTrusteeClientCreds)
          .queryParams(queryParamsEmail)
          .get("/user/search")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
  }
}
