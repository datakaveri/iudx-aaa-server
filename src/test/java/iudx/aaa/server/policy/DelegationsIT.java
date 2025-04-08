package iudx.aaa.server.policy;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.registration.Constants.*;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static org.hamcrest.Matchers.*;

import io.restassured.http.ContentType;
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
 * Integration tests for delegation creation, listing and deletion. Also tests user getting delegate
 * role and lifecycle when delegation is created/deleted.
 */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class DelegationsIT {
  private static String ALPHA_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static String BRAVO_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static String DUMMY_SERVER_NO_ONE_HAS_ROLES =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

  /** Has admin, trustee roles. */
  private static String tokenRoles;

  private static String tokenNoRoles;

  private static String consumerToken;
  private static String providerToken;

  private static String consumerEmail;
  private static String providerEmail;

  @BeforeAll
  static void setup(KcAdminInt kc) {
    String rolesEmail = IntegTestHelpers.email();

    tokenNoRoles = kc.createUser(IntegTestHelpers.email());

    tokenRoles = kc.createUser(rolesEmail);

    consumerEmail = IntegTestHelpers.email();
    providerEmail = IntegTestHelpers.email();
    consumerToken = kc.createUser(consumerEmail);
    providerToken = kc.createUser(providerEmail);

    // create Alpha, Bravo and Charlie RSs
    JsonObject rsReqAlpha =
        new JsonObject().put("name", "name").put("url", ALPHA_SERVER).put("owner", rolesEmail);

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
        new JsonObject().put("name", "name").put("url", BRAVO_SERVER).put("owner", rolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqBravo.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Bravo RS server", is(201)));

    JsonObject rsReqDummy =
        new JsonObject()
            .put("name", "name")
            .put("url", DUMMY_SERVER_NO_ONE_HAS_ROLES)
            .put("owner", rolesEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqDummy.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Dummy RS server", is(201)));

    // create consumer roles for Alpha and Bravo server
    JsonObject consReq =
        new JsonObject().put("consumer", new JsonArray().add(ALPHA_SERVER).add(BRAVO_SERVER));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(consReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(
            describedAs("Setup - Added consumer roles for Alpha, Bravo RS Server", is(200)));

    // create provider roles for Alpha and Bravo server
    JsonObject provReq =
        new JsonObject().put("provider", new JsonArray().add(ALPHA_SERVER).add(BRAVO_SERVER));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(provReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(
            describedAs("Setup - Added provider roles for Alpha, Bravo RS Server", is(200)));

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

    String provAlphaId =
        response
            .jsonPath()
            .param("email", providerEmail)
            .param("rsUrl", ALPHA_SERVER)
            .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");
    String provBravoId =
        response
            .jsonPath()
            .param("email", providerEmail)
            .param("rsUrl", BRAVO_SERVER)
            .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");

    JsonObject approveReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                            .put("id", provAlphaId))
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                            .put("id", provBravoId)));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(approveReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(describedAs("Setup - Approve provider on Alpha RS server", is(200)));

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
  @DisplayName("Create delegation - admin, trustee, COS admin cannot call API")
  void createDelegationRolesCannotCall(KcAdminInt kc) {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_CREATE_DELEGATE_ROLES));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_CREATE_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("Create delegation - no roles cannot call API")
  void createDelegationNoRolesCannotCall() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_CREATE_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("Create delegation - missing token")
  void createDelegationMissingToken() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Create delegation - missing body")
  void createDelegationMissingBody() {
    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create delegation - missing keys")
  void createDelegationMissingKeys() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    delegReq =
        new JsonObject()
            .put("userEmail", IntegTestHelpers.email())
            .put("resSerUrl", ALPHA_SERVER)
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create delegation - invalid keys")
  void createDelegationInvalidKeys() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", RandomStringUtils.randomAlphabetic(20))
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", "%sasesajdn")
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.ADMIN.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // uppercase email not allowed
    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email().toUpperCase())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    // uppercase server URL not allowed
    delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER.toUpperCase())
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create delegation - duplicates")
  void createDelegationDuplicates() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", consumerEmail)
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", consumerEmail)
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create delegation - RS does not exist")
  void createDelegationResourceServerDoesNotExist() {
    String rsNotExist = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", rsNotExist)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("detail", equalTo(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("context." + ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE, contains(rsNotExist));
  }

  @Test
  @DisplayName("Create delegation - user does not have the role to be delegated")
  void createDelegationUserNoHaveDelegatedRole() {
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", BRAVO_SERVER)
                            .put("role", Roles.PROVIDER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("detail", equalTo(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("context." + ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE, contains(ALPHA_SERVER));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("detail", equalTo(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("context." + ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE, contains(BRAVO_SERVER));
  }

  @Test
  @DisplayName("Create delegation - user has role, but not for delegated RS")
  void createDelegationEmailUserNoHaveRoleForDelegatedRs() {
    JsonObject consumerDelegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", DUMMY_SERVER_NO_ONE_HAS_ROLES)
                            .put("role", Roles.CONSUMER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(consumerDelegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("detail", equalTo(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body(
            "context." + ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
            contains(DUMMY_SERVER_NO_ONE_HAS_ROLES));

    JsonObject providerDelegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.PROVIDER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", IntegTestHelpers.email())
                            .put("resSerUrl", DUMMY_SERVER_NO_ONE_HAS_ROLES)
                            .put("role", Roles.PROVIDER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(providerDelegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body("detail", equalTo(ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE))
        .body(
            "context." + ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
            contains(DUMMY_SERVER_NO_ONE_HAS_ROLES));
  }

  @Test
  @DisplayName("Create delegation - email doesn't exist on UAC Keycloak")
  void createDelegationEmailNotExist() {
    String badEmail = IntegTestHelpers.email();

    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", badEmail)
                            .put("resSerUrl", BRAVO_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", providerEmail)
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(consumerToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK))
        .body("detail", equalTo(ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK))
        .body(
            "context." + ERR_CONTEXT_NOT_FOUND_EMAILS, containsInAnyOrder(badEmail.toLowerCase()));
  }

  @Test
  @DisplayName("List delegation - admin, trustee, COS admin cannot call API")
  void listDelegationRolesCannotCall(KcAdminInt kc) {
    given()
        .auth()
        .oauth2(tokenRoles)
        .when()
        .get("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .when()
        .get("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("List delegation - no roles cannot call API")
  void listDelegationNoRolesCannotCall() {
    given()
        .auth()
        .oauth2(tokenNoRoles)
        .when()
        .get("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("List delegation - no token")
  void listDelegationNoToken() {
    given()
        .when()
        .get("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Delete delegation - admin, trustee, COS admin cannot call API")
  void deleteDelegationRolesCannotCall(KcAdminInt kc) {
    JsonObject deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString())));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_DEL_DELEGATE_ROLES));

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_DEL_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("Delete delegation - no roles cannot call API")
  void deleteDelegationNoRolesCannotCall() {
    JsonObject deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString())));

    given()
        .auth()
        .oauth2(tokenNoRoles)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
        .body("detail", equalTo(ERR_DETAIL_DEL_DELEGATE_ROLES));
  }

  @Test
  @DisplayName("Delete delegation - missing token")
  void deleteDelegationMissingToken() {
    JsonObject deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString())));

    given()
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Delete delegation - missing body")
  void deleteDelegationMissingBody() {
    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Delete delegation - invalid and missing keys")
  void deleteDelegationInvalidMissingKeys() {
    JsonObject deleteReq = new JsonObject().put("id", UUID.randomUUID().toString());

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(new JsonObject().put("something", UUID.randomUUID().toString())));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray().add(new JsonObject().put("id", RandomStringUtils.random(20))));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Delete delegation - duplicates")
  void deleteDelegationDuplicates() {
    String badId = UUID.randomUUID().toString();
    JsonObject deleteReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(new JsonObject().put("id", badId))
                    .add(new JsonObject().put("id", badId)));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Delete delegation - random delegation ID")
  void deleteDelegationRandomDelegId() {
    String badId = UUID.randomUUID().toString();

    JsonObject deleteReq =
        new JsonObject().put("request", new JsonArray().add(new JsonObject().put("id", badId)));

    given()
        .auth()
        .oauth2(providerToken)
        .contentType(ContentType.JSON)
        .body(deleteReq.toString())
        .when()
        .delete("/delegations")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
        .body("title", equalTo(ERR_TITLE_INVALID_ID))
        .body("detail", equalTo(badId));
  }

  @Test
  @DisplayName("User has both provider and consumer roles - test create, list delegations")
  void userBothConsProvRole(KcAdminInt kc) {
    // need to set up provider+consumer user and delegate user

    String provConsEmail = IntegTestHelpers.email();
    String provConsToken = kc.createUser(provConsEmail);

    String delegateEmail = IntegTestHelpers.email();
    kc.createUser(delegateEmail);

    // create provider roles for Alpha, consumer for Bravo
    JsonObject consReq =
        new JsonObject()
            .put("provider", new JsonArray().add(ALPHA_SERVER))
            .put("consumer", new JsonArray().add(BRAVO_SERVER));

    given()
        .auth()
        .oauth2(provConsToken)
        .contentType(ContentType.JSON)
        .body(consReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(
            describedAs(
                "Setup - Added provider role for Alpha RS Server, consumer for Bravo", is(200)));

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

    String provAlphaId =
        response
            .jsonPath()
            .param("email", provConsEmail)
            .param("rsUrl", ALPHA_SERVER)
            .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");

    JsonObject approveReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                            .put("id", provAlphaId)));

    given()
        .auth()
        .oauth2(tokenRoles)
        .contentType(ContentType.JSON)
        .body(approveReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(describedAs("Setup - Approve provider on Alpha RS server", is(200)));

    // create provider delegation for alpha and consumer delegation for bravo
    JsonObject delegReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("userEmail", delegateEmail)
                            .put("resSerUrl", ALPHA_SERVER)
                            .put("role", Roles.PROVIDER.toString().toLowerCase()))
                    .add(
                        new JsonObject()
                            .put("userEmail", delegateEmail)
                            .put("resSerUrl", BRAVO_SERVER)
                            .put("role", Roles.CONSUMER.toString().toLowerCase())));

    given()
        .auth()
        .oauth2(provConsToken)
        .contentType(ContentType.JSON)
        .body(delegReq.toString())
        .when()
        .post("/delegations")
        .then()
        .statusCode(201)
        .body("type", equalTo(Urn.URN_SUCCESS.toString()));

    given()
        .auth()
        .oauth2(provConsToken)
        .contentType(ContentType.JSON)
        .when()
        .get("/delegations")
        .then()
        .statusCode(200)
        .body("type", equalTo(Urn.URN_SUCCESS.toString()))
        .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
        .body(
            "results.findAll { it.url == '%s' && it.role == '%s'}.user.email",
            withArgs(ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase()),
            containsInAnyOrder(delegateEmail))
        .body(
            "results.findAll { it.url == '%s' && it.role == '%s'}.user.email",
            withArgs(BRAVO_SERVER, Roles.CONSUMER.toString().toLowerCase()),
            containsInAnyOrder(delegateEmail));
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName("Create, list, delete delegations")
  class DelegationLifecycle {

    String delegateAliceEmail = IntegTestHelpers.email();
    String delegateBobEmail = IntegTestHelpers.email();

    String delegateAliceToken;
    String delegateBobToken;

    @BeforeAll
    void setup(KcAdminInt kc) {
      delegateAliceToken = kc.createUser(delegateAliceEmail);
      delegateBobToken = kc.createUser(delegateBobEmail);
    }

    @Test
    @Order(1)
    @DisplayName("Provider and consumer user list delegations - no delegations for Alice, Bob")
    void listConsProvNoDelegsYet() {
      given()
          .auth()
          .oauth2(providerToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.user.email", not(containsInAnyOrder(delegateAliceEmail, delegateBobEmail)));

      given()
          .auth()
          .oauth2(consumerToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.user.email", not(containsInAnyOrder(delegateAliceEmail, delegateBobEmail)));
    }

    @Test
    @Order(2)
    @DisplayName("Alice and Bob try listing, cannot list yet because no delegate role")
    void listConsAliceBobFail() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));
    }

    @Test
    @Order(3)
    @DisplayName("Consumer creates Alice as consumer delegate for Alpha server")
    void createConsumerDelegateSuccess() {
      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));
    }

    @Test
    @Order(4)
    @DisplayName("Provider creates Alice as provider delegate for Alpha server")
    void createProviderDelegateSuccess() {
      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
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
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));
    }

    @Test
    @Order(5)
    @DisplayName(
        "Consumer and provider lists delegates - delegations exist for Alice for Alpha server")
    void delegatorListDelegates() {
      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}",
              withArgs(ALPHA_SERVER, Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail),
              not(nullValue()))
          .body("results", everyItem(hasKey("id")))
          .body("results.owner.email", everyItem(is(consumerEmail)))
          .body("results.owner", everyItem(hasKey("id")))
          .body("results.owner.name", everyItem(hasKey("firstName")))
          .body("results.owner.name", everyItem(hasKey("lastName")))
          .body("results.user", everyItem(hasKey("id")))
          .body("results.user", everyItem(hasKey("email")))
          .body("results.user.name", everyItem(hasKey("firstName")))
          .body("results.user.name", everyItem(hasKey("lastName")));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}",
              withArgs(ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail),
              not(nullValue()))
          .body("results", everyItem(hasKey("id")))
          .body("results.owner.email", everyItem(is(providerEmail)))
          .body("results.owner", everyItem(hasKey("id")))
          .body("results.owner.name", everyItem(hasKey("firstName")))
          .body("results.owner.name", everyItem(hasKey("lastName")))
          .body("results.user", everyItem(hasKey("id")))
          .body("results.user", everyItem(hasKey("email")))
          .body("results.user.name", everyItem(hasKey("firstName")))
          .body("results.user.name", everyItem(hasKey("lastName")));
    }

    @Test
    @Order(6)
    @DisplayName(
        "Delegates list delegations - consumer delegation and provider delegation exists Alpha server for Alice, "
            + "Bob gets 401")
    void delegateListDelegates() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.find { it.url == '%s' && it.role == '%s' }",
              withArgs(ALPHA_SERVER, Roles.CONSUMER.toString().toLowerCase(), consumerEmail),
              not(nullValue()))
          .body(
              "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}",
              withArgs(ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), providerEmail),
              not(nullValue()))
          .body("results", everyItem(hasKey("id")))
          .body("results.user", everyItem(hasKey("id")))
          .body("results.user.email", everyItem(is(delegateAliceEmail)))
          .body("results.user.name", everyItem(hasKey("firstName")))
          .body("results.user.name", everyItem(hasKey("lastName")))
          .body("results.owner.email", containsInAnyOrder(consumerEmail, providerEmail))
          .body("results.owner", everyItem(hasKey("id")))
          .body("results.owner.name", everyItem(hasKey("firstName")))
          .body("results.owner.name", everyItem(hasKey("lastName")));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()));
    }

    @Test
    @Order(7)
    @DisplayName(
        "Consumer tries setting Alice as delegate for Bravo AND Alpha in same request - fails because latter exists")
    void consumerDelegateAlreadyExists() {
      String consDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(consumerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail);

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_DUPLICATE_DELEGATION))
          .body("detail", equalTo(ERR_DETAIL_DUPLICATE_DELEGATION))
          .body(
              "context." + ERR_CONTEXT_EXISTING_DELEGATION_IDS,
              containsInAnyOrder(consDelegIdAliceAlpha));
    }

    @Test
    @Order(8)
    @DisplayName(
        "Provider tries setting Alice as delegate for Bravo AND Alpha in same request - fails because latter exists")
    void providerDelegateAlreadyExists() {
      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(providerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail);

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()))
          .body("title", equalTo(ERR_TITLE_DUPLICATE_DELEGATION))
          .body("detail", equalTo(ERR_DETAIL_DUPLICATE_DELEGATION))
          .body(
              "context." + ERR_CONTEXT_EXISTING_DELEGATION_IDS,
              containsInAnyOrder(provDelegIdAliceAlpha));
    }

    @Test
    @Order(9)
    @DisplayName(
        "Provider creates Alice and Bob as provider delegate for Bravo server - repeat fails")
    void createProviderDelegateMultipleSuccess() {
      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateBobEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()));
    }

    @Test
    @Order(10)
    @DisplayName(
        "Consumer creates Alice and Bob as consumer delegate for Bravo server - repeat fails")
    void createConsumerDelegateMultipleSuccess() {
      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateBobEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(409)
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString()));
    }

    @Test
    @Order(11)
    @DisplayName(
        "Consumer and provider lists delegates - delegations exist for Alice for Alpha, Bravo server and "
            + "Bob for Bravo server")
    void delegatorListDelegatesAfterMultiInsert() {
      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail),
              containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), delegateBobEmail),
              containsInAnyOrder(BRAVO_SERVER));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail),
              containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), delegateBobEmail),
              containsInAnyOrder(BRAVO_SERVER));
    }

    @Test
    @Order(12)
    @DisplayName(
        "Alice and Bob lists delegations - delegations exist for Alice for Alpha, Bravo server and "
            + "Bob for Bravo server")
    void delegatesListDelegatesAfterMultiInsert() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), consumerEmail),
              containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), providerEmail),
              containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), consumerEmail),
              containsInAnyOrder(BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), providerEmail),
              containsInAnyOrder(BRAVO_SERVER));
    }

    @Test
    @Order(13)
    @DisplayName("Consumer trying to delete delegation owned by provider user")
    void deleteDelegNotOwned() {
      String consDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(consumerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail);

      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(providerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail);

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(new JsonObject().put("id", consDelegIdAliceAlpha))
                      .add(new JsonObject().put("id", provDelegIdAliceAlpha)));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ID))
          .body("detail", containsString(provDelegIdAliceAlpha));
    }

    @Test
    @Order(14)
    @DisplayName("Consumer delete delegation for Alpha for Alice - repeat fails")
    void consumerDeleteSingleDeleg() {
      String consDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(consumerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail);

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray().add(new JsonObject().put("id", consDelegIdAliceAlpha)));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELETE_DELE));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ID))
          .body("detail", containsString(consDelegIdAliceAlpha));
    }

    @Test
    @Order(15)
    @DisplayName("Provider delete delegation for Alpha for Alice - repeat fails")
    void providerDeleteSingleDeleg() {
      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(providerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.user.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail);

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray().add(new JsonObject().put("id", provDelegIdAliceAlpha)));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELETE_DELE));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ID))
          .body("detail", containsString(provDelegIdAliceAlpha));
    }

    @Test
    @Order(16)
    @DisplayName(
        "Consumer and provider lists delegates - delegations gone for Alice for Alpha, still for Bravo server and "
            + "Bob for Bravo server")
    void delegatorListDelegatesAfterDelete() {
      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), delegateAliceEmail),
              containsInAnyOrder(BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), delegateBobEmail),
              containsInAnyOrder(BRAVO_SERVER));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), delegateAliceEmail),
              containsInAnyOrder(BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.user.email == '%s'}.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), delegateBobEmail),
              containsInAnyOrder(BRAVO_SERVER));
    }

    @Test
    @Order(17)
    @DisplayName("Alice lists delegations - delegations gone for Alpha, only Bravo")
    void delegateAliceListDelegatesAfterDelete() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .contentType(ContentType.JSON)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.CONSUMER.toString().toLowerCase(), consumerEmail),
              containsInAnyOrder(BRAVO_SERVER))
          .body(
              "results.findAll { it.role == '%s' && it.owner.email == '%s' }.url",
              withArgs(Roles.PROVIDER.toString().toLowerCase(), providerEmail),
              containsInAnyOrder(BRAVO_SERVER));
    }

    @Test
    @Order(18)
    @DisplayName("Consumer deletes Bravo for Alice and Bob - repeat fails")
    void consumerDeleteMultipleDeleg() {
      Response resp =
          given()
              .auth()
              .oauth2(consumerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .response();

      String aliceBravoId =
          resp.jsonPath()
              .param("email", delegateAliceEmail)
              .param("role", Roles.CONSUMER.toString().toLowerCase())
              .param("url", BRAVO_SERVER)
              .getString(
                  "results.find { it.url == url && it.role == role && it.user.email == email}.id");
      String bobBravoId =
          resp.jsonPath()
              .param("email", delegateBobEmail)
              .param("role", Roles.CONSUMER.toString().toLowerCase())
              .param("url", BRAVO_SERVER)
              .getString(
                  "results.find { it.url == url && it.role == role && it.user.email == email}.id");

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(new JsonObject().put("id", aliceBravoId))
                      .add(new JsonObject().put("id", bobBravoId)));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELETE_DELE));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ID));
    }

    @Test
    @Order(19)
    @DisplayName("Provider deletes Bravo for Alice and Bob - repeat fails")
    void providerDeleteMultipleDeleg() {
      Response resp =
          given()
              .auth()
              .oauth2(providerToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .response();

      String aliceBravoId =
          resp.jsonPath()
              .param("email", delegateAliceEmail)
              .param("role", Roles.PROVIDER.toString().toLowerCase())
              .param("url", BRAVO_SERVER)
              .getString(
                  "results.find { it.url == url && it.role == role && it.user.email == email}.id");
      String bobBravoId =
          resp.jsonPath()
              .param("email", delegateBobEmail)
              .param("role", Roles.PROVIDER.toString().toLowerCase())
              .param("url", BRAVO_SERVER)
              .getString(
                  "results.find { it.url == url && it.role == role && it.user.email == email}.id");

      JsonObject delegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(new JsonObject().put("id", aliceBravoId))
                      .add(new JsonObject().put("id", bobBravoId)));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELETE_DELE));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(delegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ID));
    }

    @Test
    @Order(20)
    @DisplayName(
        "Provider and consumer user list delegations after deleting - no delegations for Alice, Bob")
    void listConsProvAfterDeletes() {
      given()
          .auth()
          .oauth2(providerToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.user.email", not(containsInAnyOrder(delegateAliceEmail, delegateBobEmail)));

      given()
          .auth()
          .oauth2(consumerToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_LIST_DELEGS))
          .body(
              "results.user.email", not(containsInAnyOrder(delegateAliceEmail, delegateBobEmail)));
    }

    @Test
    @Order(21)
    @DisplayName("Alice and Bob try listing after deleting, cannot list because lost delegate role")
    void listConsAliceBobAfterDeletes() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .when()
          .get("/delegations")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLES))
          .body("detail", equalTo(ERR_DETAIL_LIST_DELEGATE_ROLES));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @TestMethodOrder(OrderAnnotation.class)
  @DisplayName(
      "Delegate lifecycle as delegations are created and deleted + Delegation ID for token requests tests")
  class DelegateLifecycleAndDelegationIdTest {

    String delegateAliceEmail = IntegTestHelpers.email();
    String delegateBobEmail = IntegTestHelpers.email();

    String delegateAliceToken;
    String delegateBobToken;

    // get client creds for tokenRoles to test get delegate emails API
    Map<String, String> rolesTrusteeClientCreds;

    @BeforeAll
    void setup(KcAdminInt kc) {
      delegateAliceToken = kc.createUser(delegateAliceEmail);
      delegateBobToken = kc.createUser(delegateBobEmail);

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
    @Order(1)
    @DisplayName("Alice and Bob have no roles when listing")
    void listingNoRoles() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
    }

    @Test
    @Order(2)
    @DisplayName("Provider set Alice, Bob for Alpha, Consumer set Alice, Bob for Bravo")
    void setupDelegations() {
      JsonObject provDelegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateBobEmail)
                              .put("resSerUrl", ALPHA_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      JsonObject consDelegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateAliceEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase()))
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateBobEmail)
                              .put("resSerUrl", BRAVO_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(provDelegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(consDelegReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));
    }

    @Test
    @Order(3)
    @DisplayName("Delegates list roles - has delegate role for both Alpha and Bravo servers")
    void listRolesAfterCreate() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("results.roles", hasItem(Roles.DELEGATE.toString().toLowerCase()))
          .body(
              "results.rolesToRsMapping.delegate", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("results.roles", hasItem(Roles.DELEGATE.toString().toLowerCase()))
          .body(
              "results.rolesToRsMapping.delegate", containsInAnyOrder(ALPHA_SERVER, BRAVO_SERVER));
    }

    @Test
    @Order(5)
    @DisplayName(
        "Delegation ID test - Alice gets provider RS token for Alpha, Bob gets consumer RS token for Bravo")
    void delegIdTestSuccess() {
      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(delegateAliceToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), providerEmail);

      String consDelegIdBobBravo =
          given()
              .auth()
              .oauth2(delegateBobToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  BRAVO_SERVER, Roles.CONSUMER.toString().toLowerCase(), consumerEmail);

      JsonObject aliceTokenReq =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      JsonObject bobTokenReq =
          new JsonObject()
              .put("itemId", BRAVO_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateAliceToken)
          .header("delegationId", provDelegIdAliceAlpha)
          .contentType(ContentType.JSON)
          .body(aliceTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .header("delegationId", consDelegIdBobBravo)
          .contentType(ContentType.JSON)
          .body(bobTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));
    }

    @Test
    @Order(6)
    @DisplayName("Delegation ID test - Non existent delegation ID")
    void delegIdTestNonExistDelegId() {
      JsonObject aliceTokenReq =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateAliceToken)
          .header("delegationId", UUID.randomUUID().toString())
          .contentType(ContentType.JSON)
          .body(aliceTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_DELEGATE))
          .body("detail", equalTo(ERR_DELEGATE));
    }

    @Test
    @Order(7)
    @DisplayName("Delegation ID test - Invalid delegation ID")
    void delegIdTestInvalidDelegId() {
      JsonObject aliceTokenReq =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateAliceToken)
          .header("delegationId", RandomStringUtils.random(10))
          .contentType(ContentType.JSON)
          .body(aliceTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

      given()
          .auth()
          .oauth2(delegateAliceToken)
          .header("delegationId", "")
          .contentType(ContentType.JSON)
          .body(aliceTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }

    @Test
    @Order(8)
    @DisplayName("Delegation ID test - Bob making request using delegation ID associated w/ Alice")
    void delegIdTestNotOwned() {
      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(delegateAliceToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), providerEmail);

      JsonObject bobTokenReq =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateBobToken)
          .header("delegationId", provDelegIdAliceAlpha)
          .contentType(ContentType.JSON)
          .body(bobTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_DELEGATE))
          .body("detail", equalTo(ERR_DELEGATE));
    }

    @Test
    @Order(9)
    @DisplayName(
        "Get delegate emails - provider and consumer calls will return both delegates each")
    void getDelegateEmails() {
      String providerUserId = IntegTestHelpers.getUserIdFromKcToken(providerToken);
      Map<String, String> provQueryParams =
          Map.of(
              "userId",
              providerUserId,
              "resourceServer",
              ALPHA_SERVER,
              "role",
              Roles.PROVIDER.toString().toLowerCase());

      String consumerUserId = IntegTestHelpers.getUserIdFromKcToken(consumerToken);
      Map<String, String> consQueryParams =
          Map.of(
              "userId",
              consumerUserId,
              "resourceServer",
              BRAVO_SERVER,
              "role",
              Roles.CONSUMER.toString().toLowerCase());

      given()
          .when()
          .headers(rolesTrusteeClientCreds)
          .queryParams(provQueryParams)
          .get("/delegations/emails")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", containsInAnyOrder(delegateAliceEmail, delegateBobEmail));

      given()
          .when()
          .headers(rolesTrusteeClientCreds)
          .queryParams(consQueryParams)
          .get("/delegations/emails")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", containsInAnyOrder(delegateAliceEmail, delegateBobEmail));
    }

    @Test
    @Order(10)
    @DisplayName(
        "Delegation ID test - Delete all delegations for Bob, delete provider deleg Alpha"
            + " - pass deleted delegation ID header")
    void delegIdTestDeletedDelegationId() {
      String provDelegIdAliceAlpha =
          given()
              .auth()
              .oauth2(delegateAliceToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), providerEmail);

      String consDelegIdBobBravo =
          given()
              .auth()
              .oauth2(delegateBobToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  BRAVO_SERVER, Roles.CONSUMER.toString().toLowerCase(), consumerEmail);

      String provDelegIdBobAlpha =
          given()
              .auth()
              .oauth2(delegateBobToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .path(
                  "results.find { it.url == '%s' && it.role == '%s' && it.owner.email == '%s'}.id",
                  ALPHA_SERVER, Roles.PROVIDER.toString().toLowerCase(), providerEmail);

      // deleting delegations
      JsonObject provDelegReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(new JsonObject().put("id", provDelegIdAliceAlpha))
                      .add(new JsonObject().put("id", provDelegIdBobAlpha)));

      given()
          .auth()
          .oauth2(providerToken)
          .contentType(ContentType.JSON)
          .body(provDelegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200);

      JsonObject consDelegReq =
          new JsonObject()
              .put("request", new JsonArray().add(new JsonObject().put("id", consDelegIdBobBravo)));

      given()
          .auth()
          .oauth2(consumerToken)
          .contentType(ContentType.JSON)
          .body(consDelegReq.toString())
          .when()
          .delete("/delegations")
          .then()
          .statusCode(200);

      // test getting token w/ deleted delegation ID
      JsonObject aliceTokenReq =
          new JsonObject()
              .put("itemId", ALPHA_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateAliceToken)
          .header("delegationId", provDelegIdAliceAlpha)
          .contentType(ContentType.JSON)
          .body(aliceTokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_DELEGATE))
          .body("detail", equalTo(ERR_DELEGATE));
    }

    @Test
    @Order(11)
    @DisplayName("Delegates list roles - Alice has delegation for Bravo, Bob has no roles anymore")
    void listRolesAfterDelete() {
      given()
          .auth()
          .oauth2(delegateAliceToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("results.roles", hasItem(Roles.DELEGATE.toString().toLowerCase()))
          .body("results.rolesToRsMapping.delegate", containsInAnyOrder(BRAVO_SERVER));

      given()
          .auth()
          .oauth2(delegateBobToken)
          .when()
          .get("/user/roles")
          .then()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
    }

    @Test
    @Order(12)
    @DisplayName(
        "Get delegate emails - provider has no delegates anymore, so empty, consumer has Alice as delegate on Bravo")
    void getDelegateEmailsAfterDelete() {
      String providerUserId = IntegTestHelpers.getUserIdFromKcToken(providerToken);
      Map<String, String> provQueryParams =
          Map.of(
              "userId",
              providerUserId,
              "resourceServer",
              ALPHA_SERVER,
              "role",
              Roles.PROVIDER.toString().toLowerCase());

      String consumerUserId = IntegTestHelpers.getUserIdFromKcToken(consumerToken);
      Map<String, String> consQueryParams =
          Map.of(
              "userId",
              consumerUserId,
              "resourceServer",
              BRAVO_SERVER,
              "role",
              Roles.CONSUMER.toString().toLowerCase());

      given()
          .when()
          .headers(rolesTrusteeClientCreds)
          .queryParams(provQueryParams)
          .get("/delegations/emails")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));

      given()
          .when()
          .headers(rolesTrusteeClientCreds)
          .queryParams(consQueryParams)
          .get("/delegations/emails")
          .then()
          .statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", containsInAnyOrder(delegateAliceEmail));
    }
  }
}
