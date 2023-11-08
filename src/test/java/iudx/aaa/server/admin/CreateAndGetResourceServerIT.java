package iudx.aaa.server.admin;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.admin.Constants.*;
import static iudx.aaa.server.registration.Constants.*;
import static org.hamcrest.Matchers.*;

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
import iudx.aaa.server.registration.RestAssuredConfigExtension;
import java.util.List;
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
 * Integration tests for creation and listing of resource servers. Also testing user getting the
 * admin role.
 */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class CreateAndGetResourceServerIT {

  /** Token with consumer, provider, admin, trustee and delegate roles. */
  private static String tokenRoles;

  /** Token w/ no roles. */
  private static String tokenNoRoles;

  @BeforeAll
  static void setup(KcAdminInt kc) {
    String DUMMY_SERVER = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    tokenNoRoles = kc.createUser(IntegTestHelpers.email());

    String email = IntegTestHelpers.email();
    tokenRoles = kc.createUser(email);

    // create RS
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER).put("owner", email);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReq.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));

    // create consumer, provider roles
    JsonObject consReq =
        new JsonObject()
            .put("consumer", new JsonArray().add(DUMMY_SERVER))
            .put("provider", new JsonArray().add(DUMMY_SERVER));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(consReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added consumer, provider", is(200)));

    String providerRegId =
        given()
            .auth()
            .oauth2(tokenRoles)
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
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(approveReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(describedAs("Setup - Approve provider", is(200)));

    // create APD
    JsonObject apdReq =
        new JsonObject().put("name", "name").put("url", "apd" + DUMMY_SERVER).put("owner", email);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(apdReq.toString())
        .when()
        .post("/apd")
        .then()
        .statusCode(describedAs("Setup - Created dummy APD", is(201)));

    // create delegation
    String delegatorEmail = IntegTestHelpers.email();
    String delegatorTok = kc.createUser(delegatorEmail);

    JsonObject delegatorConsReq =
        new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));

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
                            .put("userEmail", email)
                            .put("resSerUrl", DUMMY_SERVER)
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
  }

  @Test
  @DisplayName("Create Resource Server - No token sent")
  void createRsNoToken(KcAdminInt kc) {
    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "some-email@gmail.com");

    given()
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Create Resource Server - User without roles cannot call API")
  void createRsNoRoles(KcAdminInt kc) {
    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "some-email@gmail.com");

    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE))
        .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE));
  }

  @Test
  @DisplayName(
      "Create Resource Server - User with consumer, provider, admin, delegate, trustee roles cannot call API")
  void createRsRolesNotAllowed(KcAdminInt kc) {
    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "some-email@gmail.com");

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE))
        .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE));
  }

  @Test
  @DisplayName("Create Resource Server - Empty body")
  void createRsEmptyBody(KcAdminInt kc) {

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create Resource Server - Missing keys")
  void createRsMissingKeys(KcAdminInt kc) {

    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "some-email@gmail.com");

    JsonObject missingName = body.copy();
    missingName.remove("name");
    JsonObject missingUrl = body.copy();
    missingUrl.remove("url");
    JsonObject missingOwner = body.copy();
    missingOwner.remove("owner");

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(missingName.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(missingOwner.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(missingUrl.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create Resource Server - Invalid email")
  void createRsInvalidEmail(KcAdminInt kc) {

    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "some-emailsasd12jdnamdgmail.com");

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create Resource Server - Invalid URLs - caught by OpenAPI")
  void createRsInvalidUrlOAS(KcAdminInt kc) {

    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "someemail@gmail.com");

    List<String> invalidUrls = List.of("https://url.com", "url.com/path");

    invalidUrls.forEach(
        url -> {
          body.put("url", url);

          given()
              .auth()
              .oauth2(kc.cosAdminToken)
              .contentType(ContentType.JSON)
              .body(body.toString())
              .when()
              .post("/admin/resourceservers")
              .then()
              .statusCode(400)
              .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
        });
  }

  @Test
  @DisplayName("Create Resource Server - Invalid URLs - caught by Guava lib")
  void createRsInvalidUrlGuava(KcAdminInt kc) {

    JsonObject body =
        new JsonObject()
            .put("name", "name")
            .put("url", "url.com")
            .put("owner", "someemail@gmail.com");

    List<String> invalidUrls = List.of("1url.1.2.3.3", "url....213da3123");

    invalidUrls.forEach(
        url -> {
          body.put("url", url);

          given()
              .auth()
              .oauth2(kc.cosAdminToken)
              .contentType(ContentType.JSON)
              .body(body.toString())
              .when()
              .post("/admin/resourceservers")
              .then()
              .statusCode(400)
              .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
              .body("title", equalTo(ERR_TITLE_INVALID_DOMAIN))
              .body("detail", equalTo(ERR_DETAIL_INVALID_DOMAIN));
        });
  }

  @Test
  @DisplayName("Create Resource Server - Email not registered on UAC")
  void createRsEmailNotOnUac(KcAdminInt kc) {

    String badEmail = RandomStringUtils.randomAlphabetic(15) + "@gmail.com";
    JsonObject body =
        new JsonObject().put("name", "name").put("url", "url.com").put("owner", badEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK))
        .body("detail", equalTo(ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK))
        .body(
            "context." + ERR_CONTEXT_NOT_FOUND_EMAILS, containsInAnyOrder(badEmail.toLowerCase()));
  }

  @Nested
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Create Resource Server - Successfully created RS and testing side-effects")
  class CreateRsSuccess {

    String email = IntegTestHelpers.email();
    String adminToken;
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";

    @BeforeAll
    void setup(KcAdminInt kc) {
      adminToken = kc.createUser(email);
    }

    @Test
    @Order(1)
    @DisplayName("User w/ consumer role does not have RS URL in roles-RS mapping")
    void consNoHaveRs() {
      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.rolesToRsMapping.consumer", not(hasItem(serverUrl.toLowerCase())));
    }

    @Test
    @Order(2)
    @DisplayName(
        "User about to become admin does not have admin role and cannot call provider reg APIs")
    void newAdminUserNoAdminRoleOrProviderRegApis() {
      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
    }

    @Test
    @Order(3)
    @DisplayName("User about to become admin cannot call provider reg APIs")
    void newAdminUserNoProviderRegApis() {
      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
          .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));

      JsonObject provData =
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
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(provData.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_NOT_ADMIN))
          .body("detail", equalTo(ERR_DETAIL_NOT_ADMIN));
    }

    @Test
    @Order(4)
    @DisplayName("User about to become admin cannot get admin token")
    void newAdminUserNoToken() {
      JsonObject tokenBody =
          new JsonObject()
              .put("itemId", serverUrl)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()));
    }

    @Test
    @Order(5)
    @DisplayName("Create resource server success")
    void createRsSuccess(KcAdminInt kc) {
      JsonObject body =
          new JsonObject().put("name", serverName).put("url", serverUrl).put("owner", email);

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .body(body.toString())
          .when()
          .post("/admin/resourceservers")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_CREATED_RS))
          .body("results", hasKey("id"))
          .body("results", hasKey("owner"))
          .rootPath("results")
          .body("name", equalTo(serverName))
          .body("url", equalTo(serverUrl.toLowerCase()))
          .body("owner", hasKey("id"))
          .body("owner", hasKey("name"))
          .body("owner.name", hasKey("firstName"))
          .body("owner.name", hasKey("lastName"))
          .body("owner.email", equalTo(email.toLowerCase()));
    }

    @Test
    @Order(6)
    @DisplayName("User w/ consumer role has new RS URL in roles-RS mapping automatically")
    void consHaveRs() {
      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.rolesToRsMapping.consumer", hasItem(serverUrl.toLowerCase()));
    }

    @Test
    @Order(7)
    @DisplayName("New admin user has admin role")
    void newAdminUserHasAdminRole() {
      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_READ))
          .body("results.roles", hasItem(Roles.ADMIN.toString().toLowerCase()))
          .body("results.rolesToRsMapping.admin", hasItem(serverUrl.toLowerCase()));
    }

    @Test
    @Order(8)
    @DisplayName("New admin user can call provider reg APIs")
    void newAdminUserCanCallProviderRegApis() {
      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/admin/provider/registrations")
          .then()
          .statusCode(not(401));

      JsonObject provData =
          new JsonObject()
              .put("id", UUID.randomUUID().toString())
              .put("status", RoleStatus.APPROVED.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(provData.toString())
          .when()
          .put("/admin/provider/registrations")
          .then()
          .statusCode(not(401));
    }

    @Test
    @Order(9)
    @DisplayName("New admin user can get admin token")
    void newAdminUserGetToken() {
      JsonObject tokenBody =
          new JsonObject()
              .put("itemId", serverUrl)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminToken)
          .contentType(ContentType.JSON)
          .body(tokenBody.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));
    }
  }

  @Test
  @DisplayName("Create Resource Server - Duplicate RS")
  void createRsDuplicate(KcAdminInt kc) {

    String email = IntegTestHelpers.email();
    kc.createUser(email);
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";

    JsonObject body =
        new JsonObject().put("name", serverName).put("url", serverUrl).put("owner", email);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(201)
        .body("type", equalTo(Urn.URN_SUCCESS.toString()))
        .body("title", equalTo(SUCC_TITLE_CREATED_RS));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(body.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(409)
        .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
        .body("title", equalTo(ERR_TITLE_DOMAIN_EXISTS))
        .body("detail", equalTo(ERR_DETAIL_DOMAIN_EXISTS));
  }

  @Test
  @DisplayName("Get Resource Server - No token sent")
  void getRsNoToken(KcAdminInt kc) {

    when()
        .get("/resourceservers")
        .then()
        .statusCode(401)
        .and()
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Nested
  @DisplayName("Get Resource Server - Any user with valid token can call API")
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  class GetRsSuccess {

    String serverId;
    String email = IntegTestHelpers.email();
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";

    @Test
    @Order(1)
    @DisplayName("No user can see the RS before created")
    void tobeCreatedRsCannotBeSeen(KcAdminInt kc) {

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results.find { it.url == '%s' }", withArgs(serverUrl), is(nullValue()));

      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results.find { it.url == '%s' }", withArgs(serverUrl), is(nullValue()));

      given()
          .auth()
          .oauth2(tokenNoRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results.find { it.url == '%s' }", withArgs(serverUrl), is(nullValue()));
    }

    @Test
    @DisplayName("Create RS")
    @Order(2)
    void createRs(KcAdminInt kc) {
      kc.createUser(email);

      JsonObject body =
          new JsonObject().put("name", serverName).put("url", serverUrl).put("owner", email);

      serverId =
          given()
              .auth()
              .oauth2(kc.cosAdminToken)
              .contentType(ContentType.JSON)
              .body(body.toString())
              .when()
              .post("/admin/resourceservers")
              .then()
              .statusCode(describedAs("Created RS for Get resource server", is(201)))
              .extract()
              .path("results.id");
    }

    @Test
    @DisplayName("COS Admin can get RS")
    @Order(3)
    void cosAdminViewRs(KcAdminInt kc) {
      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName))
          .body("url", equalTo(serverUrl.toLowerCase()))
          .body("", hasKey("owner"))
          .body("owner", hasKey("id"))
          .body("owner", hasKey("name"))
          .body("owner.name", hasKey("firstName"))
          .body("owner.name", hasKey("lastName"))
          .body("owner.email", equalTo(email.toLowerCase()));
    }

    @Test
    @DisplayName("COS Admin can get RS")
    @Order(4)
    void rolesViewRs() {
      given()
          .auth()
          .oauth2(tokenRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName))
          .body("url", equalTo(serverUrl.toLowerCase()))
          .body("", hasKey("owner"))
          .body("owner", hasKey("id"))
          .body("owner", hasKey("name"))
          .body("owner.name", hasKey("firstName"))
          .body("owner.name", hasKey("lastName"))
          .body("owner.email", equalTo(email.toLowerCase()));
    }

    @Test
    @Order(5)
    @DisplayName("No roles can get RS")
    void noRolesViewRs() {
      given()
          .auth()
          .oauth2(tokenNoRoles)
          .contentType(ContentType.JSON)
          .when()
          .get("/resourceservers")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_RS_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName))
          .body("url", equalTo(serverUrl.toLowerCase()))
          .body("", hasKey("owner"))
          .body("owner", hasKey("id"))
          .body("owner", hasKey("name"))
          .body("owner.name", hasKey("firstName"))
          .body("owner.name", hasKey("lastName"))
          .body("owner.email", equalTo(email.toLowerCase()));
    }
  }
}
