package iudx.aaa.server.token;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_EVAL_FAILED;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.token.Constants.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for token creation and introspection. Uses real catalogue items that should be
 * present in the dev catalogue.
 */
@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class TokensIT {

  // real RS URL
  private static String REAL_RS_URL;
  private static String REAL_APD_URL;
  private static String REAL_COS_URL;

  private static String PROVIDER_ALPHA_EMAIL;
  private static String PROVIDER_BRAVO_EMAIL;

  private static UUID ALPHA_RES_GROUP_ID;
  private static UUID BRAVO_RES_GROUP_ID;

  private static UUID ALPHA_RES_ITEM_SECURE_ID;
  private static UUID ALPHA_RES_ITEM_OPEN_ID;

  private static UUID BRAVO_RES_ITEM_SECURE_ID;
  private static UUID BRAVO_RES_ITEM_PII_ID;

  // we register this resource server, but there are no items belonging to it on the dev cat
  private static String DUMMY_RS_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

  private static String adminRealRsToken; // will be admin of the REAL_RS_URL
  private static String adminRealRsEmail = IntegTestHelpers.email();
  private static String adminDummyRsToken; // will be admin of Dummy server
  private static String trusteeToken; // token of trustee for REAL_APD

  private static String
      providerAlphaToken; // has role for REAL_RS_URL and has items on dev catalogue
  private static String providerBravoToken; // has role for REAL_RS_URL and has items on catalogue
  private static String providerDummyRsToken; // has role for DUMMY_RS, no items on catalogue

  private static String consumerRealRsToken; // has role for REAL_RS_URL
  private static String consumerRealRsEmail = IntegTestHelpers.email();

  private static String consumerDummyRsToken; // has role for DUMMY_RS

  @BeforeAll
  static void setup(KcAdminInt kc) {
    Properties tokenEntities = new Properties();
    try {
      tokenEntities.load(
          TokensIT.class
              .getClassLoader()
              .getResourceAsStream("IntegrationTestEntities.properties"));
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    REAL_RS_URL = tokenEntities.getProperty("REAL_RS_URL");
    REAL_APD_URL = tokenEntities.getProperty("REAL_APD_URL");
    REAL_COS_URL = tokenEntities.getProperty("REAL_COS_URL");
    PROVIDER_ALPHA_EMAIL = tokenEntities.getProperty("PROVIDER_ALPHA_EMAIL");
    PROVIDER_BRAVO_EMAIL = tokenEntities.getProperty("PROVIDER_BRAVO_EMAIL");

    ALPHA_RES_GROUP_ID = UUID.fromString(tokenEntities.getProperty("ALPHA_RES_GROUP_ID"));
    BRAVO_RES_GROUP_ID = UUID.fromString(tokenEntities.getProperty("BRAVO_RES_GROUP_ID"));

    ALPHA_RES_ITEM_SECURE_ID =
        UUID.fromString(tokenEntities.getProperty("ALPHA_RES_ITEM_SECURE_ID"));
    ALPHA_RES_ITEM_OPEN_ID = UUID.fromString(tokenEntities.getProperty("ALPHA_RES_ITEM_OPEN_ID"));

    BRAVO_RES_ITEM_SECURE_ID =
        UUID.fromString(tokenEntities.getProperty("BRAVO_RES_ITEM_SECURE_ID"));
    BRAVO_RES_ITEM_PII_ID = UUID.fromString(tokenEntities.getProperty("BRAVO_RES_ITEM_PII_ID"));

    String adminDummyRsEmail = IntegTestHelpers.email();

    adminRealRsToken = kc.createUser(adminRealRsEmail);
    adminDummyRsToken = kc.createUser(adminDummyRsEmail);

    // register REAL_RS_URL
    JsonObject rsReqRealUrl =
        new JsonObject().put("name", "name").put("url", REAL_RS_URL).put("owner", adminRealRsEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqRealUrl.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Real RS server", is(201)));

    // register DUMMY_RS_URL
    JsonObject rsReqDummyServer =
        new JsonObject()
            .put("name", "name")
            .put("url", DUMMY_RS_SERVER)
            .put("owner", adminDummyRsEmail);

    given()
        .auth()
        .oauth2(kc.cosAdminToken)
        .contentType(ContentType.JSON)
        .body(rsReqDummyServer.toString())
        .when()
        .post("/admin/resourceservers")
        .then()
        .statusCode(describedAs("Setup - Created Dummy RS server", is(201)));

    // register consumers
    consumerRealRsToken = kc.createUser(consumerRealRsEmail);
    consumerDummyRsToken = kc.createUser(IntegTestHelpers.email());

    JsonObject consRealReq = new JsonObject().put("consumer", new JsonArray().add(REAL_RS_URL));
    JsonObject consDummyReq =
        new JsonObject().put("consumer", new JsonArray().add(DUMMY_RS_SERVER));

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(consRealReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added consumer with Real RS role", is(200)));

    given()
        .auth()
        .oauth2(consumerDummyRsToken)
        .contentType(ContentType.JSON)
        .body(consDummyReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added consumer with Dummy RS role", is(200)));

    // register Alpha and Bravo providers
    JsonObject provReq = new JsonObject().put("provider", new JsonArray().add(REAL_RS_URL));

    providerAlphaToken = kc.getToken(PROVIDER_ALPHA_EMAIL);
    providerBravoToken = kc.getToken(PROVIDER_BRAVO_EMAIL);

    given()
        .auth()
        .oauth2(providerAlphaToken)
        .contentType(ContentType.JSON)
        .body(provReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added provider Alpha with role for Real RS", is(200)));

    given()
        .auth()
        .oauth2(providerBravoToken)
        .contentType(ContentType.JSON)
        .body(provReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added provider Bravo with role for Real RS ", is(200)));

    // approve Alpha and Bravo providers
    Response response =
        given()
            .auth()
            .oauth2(adminRealRsToken)
            .contentType(ContentType.JSON)
            .when()
            .get("/admin/provider/registrations")
            .then()
            .statusCode(
                describedAs("Setup - Get provider reg. ID for Alpha, Bravo providers", is(200)))
            .extract()
            .response();

    String provAlphaId =
        response
            .jsonPath()
            .param("email", PROVIDER_ALPHA_EMAIL)
            .param("rsUrl", REAL_RS_URL)
            .getString("results.find { it.email == email && it.rsUrl == rsUrl}.id");
    String provBravoId =
        response
            .jsonPath()
            .param("email", PROVIDER_BRAVO_EMAIL)
            .param("rsUrl", REAL_RS_URL)
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
        .oauth2(adminRealRsToken)
        .contentType(ContentType.JSON)
        .body(approveReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(
            describedAs("Setup - Approve Alpha and Bravo providers for Real RS server", is(200)));

    // register provider for DUMMY_RS
    String providerDummyRsEmail = IntegTestHelpers.email();
    providerDummyRsToken = kc.createUser(providerDummyRsEmail);

    JsonObject provDummyRsReq =
        new JsonObject().put("provider", new JsonArray().add(DUMMY_RS_SERVER));

    given()
        .auth()
        .oauth2(providerDummyRsToken)
        .contentType(ContentType.JSON)
        .body(provDummyRsReq.toString())
        .when()
        .post("/user/roles")
        .then()
        .statusCode(describedAs("Setup - Added provider with role for Dummy RS ", is(200)));

    // approve provider for DUMMY_RS
    String dummyProviderRegId =
        given()
            .auth()
            .oauth2(adminDummyRsToken)
            .contentType(ContentType.JSON)
            .when()
            .get("/admin/provider/registrations")
            .then()
            .statusCode(describedAs("Setup - Get provider reg. ID for dummy provider", is(200)))
            .extract()
            .path("results.find { it.email == '%s' }.id", providerDummyRsEmail);

    JsonObject approveProviderDummyRsReq =
        new JsonObject()
            .put(
                "request",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("status", RoleStatus.APPROVED.toString().toLowerCase())
                            .put("id", dummyProviderRegId)));

    given()
        .auth()
        .oauth2(adminDummyRsToken)
        .contentType(ContentType.JSON)
        .body(approveProviderDummyRsReq.toString())
        .when()
        .put("/admin/provider/registrations")
        .then()
        .statusCode(describedAs("Setup - Approve provider for Dummy RS server", is(200)));

    // register APD REAL_APD_URL
    String trusteeEmail = IntegTestHelpers.email();
    trusteeToken = kc.createUser(trusteeEmail);

    JsonObject apdReq =
        new JsonObject().put("name", "name").put("url", REAL_APD_URL).put("owner", trusteeEmail);

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

  /**
   * Validate exp and iat fields in the introspected token and make sure expiry time matches the
   * configured expiry.
   *
   * @param introspectResp introspected token response
   * @return boolean if exp and iat are valid
   */
  boolean validateExpAndIat(String introspectResp) {
    JsonObject obj = new JsonObject(introspectResp).getJsonObject("results");
    return obj.getValue("exp") instanceof Integer
        && obj.getValue("iat") instanceof Integer
        && obj.getInteger("exp") - obj.getInteger("iat") == CLAIM_EXPIRY;
  }

  /**
   * Validate cons (constraints object) in introspected token.
   *
   * @param introspectResp introspected token response
   * @param isEmpty should cons be empty?
   * @return boolean if cons is valid
   */
  boolean validateCons(String introspectResp, boolean isEmpty) {
    JsonObject obj = new JsonObject(introspectResp).getJsonObject("results");
    return obj.getValue("cons") instanceof JsonObject
        && obj.getJsonObject("cons").isEmpty() == isEmpty;
  }

  @Test
  @DisplayName("User with no roles requesting token")
  void doesNotHaveAnyRoles(KcAdminInt kc) {
    String noRolesToken = kc.createUser(IntegTestHelpers.email());

    JsonObject tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(noRolesToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(404)
        .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
        .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES))
        .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES));
  }

  @Test
  @DisplayName("No Keycloak/AuthN token OR client credentials sent")
  void noTokenOrClientCreds() {
    JsonObject tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(401)
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Missing and invalid keys")
  void doesNotHaveConsumerRole() {
    JsonObject tokenReq =
        new JsonObject()
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    tokenReq =
        new JsonObject()
            .put("itemId", RandomStringUtils.random(10) + ".com")
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("itemType", RandomStringUtils.random(10))
            .put("role", Roles.CONSUMER.toString().toLowerCase());

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    tokenReq =
        new JsonObject()
            .put("itemId", DUMMY_RS_SERVER)
            .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
            .put("role", RandomStringUtils.random(10));

    given()
        .auth()
        .oauth2(consumerRealRsToken)
        .contentType(ContentType.JSON)
        .body(tokenReq.toString())
        .when()
        .post("/token")
        .then()
        .statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Nested
  @DisplayName("Consumer getting token tests")
  class ConsumerTokenTests {
    @Test
    @DisplayName("User does not have consumer role - provider requesting")
    void doesNotHaveConsumerRole() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLE_FOR_COS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_ROLE_FOR_COS));
    }

    @Test
    @DisplayName("Res Item - does not exist")
    void resItemDoesNotExist() {
      String badId = UUID.randomUUID().toString();

      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", badId)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ITEMNOTFOUND))
          .body("detail", equalTo(badId));
    }

    @Test
    @DisplayName("Res Item - invalid ID")
    void resItemInvalid() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_INPUT))
          .body("detail", equalTo(INCORRECT_ITEM_ID));
    }

    @Test
    @DisplayName("Res Item - supplied ID not a resource")
    void resItemNotResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_NOT_VALID_RESOURCE))
          .body("detail", equalTo(BRAVO_RES_GROUP_ID.toString()));
    }

    @Test
    @DisplayName("Res Item - does not have role for resource server that item belongs to")
    void resItemNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      // consumerDummyRs has consumer role for DUMMY_SERVER and not REAL_RS - so call will fail
      given()
          .auth()
          .oauth2(consumerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE));
    }

    @Test
    @DisplayName("Res Item token APD success without constraints + introspect")
    void resItemApdSuccessNoConstraints() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase())
              .put("context", new JsonObject().put("TestAllow", true));

      String token =
          given()
              .auth()
              .oauth2(consumerRealRsToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .body("iid", equalTo("ri:" + ALPHA_RES_ITEM_SECURE_ID.toString()))
              .body("rg", equalTo(ALPHA_RES_GROUP_ID.toString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }

    @Test
    @DisplayName("Res Item token APD success with constraints + introspect")
    void resItemApdSuccessConstraints() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase())
              .put("context", new JsonObject().put("TestAllowConstraints", true));

      String token =
          given()
              .auth()
              .oauth2(consumerRealRsToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .body("iid", equalTo("ri:" + BRAVO_RES_ITEM_SECURE_ID.toString()))
              .body("rg", equalTo(BRAVO_RES_GROUP_ID.toString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, false));
    }

    @Test
    @DisplayName("Res Item token APD deny")
    void resItemApdDeny() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_OPEN_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase())
              .put("context", new JsonObject().put("TestDeny", true));

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_APD_EVAL_FAILED));
    }

    @Test
    @DisplayName("Res Item token APD deny needs interaction + introspect")
    void resItemApdDenyNeedsInteraction() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase())
              .put("context", new JsonObject().put("TestDenyNInteraction", true));

      String token =
          given()
              .auth()
              .oauth2(consumerRealRsToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(403)
              .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
              .body("title", equalTo(ERR_TITLE_APD_INTERACT_REQUIRED))
              .body("detail", equalTo(ERR_DETAIL_APD_INTERACT_REQUIRED))
              .body("context", hasKey("expiry"))
              .body("context", hasKey("link"))
              .body("context.server", equalTo(REAL_APD_URL))
              .extract()
              .path("context.apdToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_APD_URL))
              .body("sid", not(emptyString()))
              .body("link", not(emptyString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a resource server URL")
    void resServerItemIdNotValidUrl() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a existing RS URL")
    void resServerItemIdNotExist() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - no role for RS")
    void resServerNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      // consumerDummy has consumer role for only DUMMY RS
      given()
          .auth()
          .oauth2(consumerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
    }

    @Test
    @DisplayName("Res Server token success + introspect")
    void resServerTokenSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.CONSUMER.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(consumerRealRsToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .body("iid", equalTo("rs:" + REAL_RS_URL))
              .body("userInfo.email", equalTo(consumerRealRsEmail))
              .body("userInfo.name.firstName", not(emptyOrNullString()))
              .body("userInfo.name.lastName", not(emptyOrNullString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }

  @Nested
  @DisplayName("Provider getting token tests")
  class ProviderTokenTests {
    @Test
    @DisplayName("User does not have provider role - consumer requesting")
    void doesNotHaveConsumerRole() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLE_FOR_COS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_ROLE_FOR_COS));
    }

    @Test
    @DisplayName("Res Item - does not exist")
    void resItemDoesNotExist() {
      String badId = UUID.randomUUID().toString();

      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", badId)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ITEMNOTFOUND))
          .body("detail", equalTo(badId));
    }

    @Test
    @DisplayName("Res Item - invalid ID")
    void resItemInvalid() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_INPUT))
          .body("detail", equalTo(INCORRECT_ITEM_ID));
    }

    @Test
    @DisplayName("Res Item - supplied ID not a resource")
    void resItemNotResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_NOT_VALID_RESOURCE))
          .body("detail", equalTo(BRAVO_RES_GROUP_ID.toString()));
    }

    @Test
    @DisplayName("Res Item - does not have role for resource server that item belongs to")
    void resItemNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      // providerDummyRs has provider role for DUMMY_SERVER and not REAL_RS - so call will fail
      given()
          .auth()
          .oauth2(providerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE));
    }

    @Test
    @DisplayName("Res Item - does not have own resource")
    void resItemNotOwnResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      // Alpha provider does not own resource belonging to Bravo provider
      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(NOT_RES_OWNER));
    }

    @Test
    @DisplayName("Res Item - cannot access PII resource")
    void resItemNoAccessToPiiResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_PII_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerBravoToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES));
    }

    @Test
    @DisplayName("Res Item success + introspect")
    void resItemSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(providerAlphaToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(providerAlphaToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.PROVIDER.toString().toLowerCase()))
              .body("iid", equalTo("ri:" + ALPHA_RES_ITEM_SECURE_ID.toString()))
              .body("rg", equalTo(ALPHA_RES_GROUP_ID.toString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a resource server URL")
    void resServerItemIdNotValidUrl() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a existing RS URL")
    void resServerItemIdNotExist() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - no role for RS")
    void resServerNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      // providerDummyRs has provider role for only DUMMY RS
      given()
          .auth()
          .oauth2(providerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
    }

    @Test
    @DisplayName("Res Server token success + introspect")
    void resServerTokenSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.PROVIDER.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(providerBravoToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(providerBravoToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("iid", equalTo("rs:" + REAL_RS_URL))
              .body("role", equalTo(Roles.PROVIDER.toString().toLowerCase()))
              .body("userInfo.email", equalTo(PROVIDER_BRAVO_EMAIL))
              .body("userInfo.name.firstName", not(emptyOrNullString()))
              .body("userInfo.name.lastName", not(emptyOrNullString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }

  @Nested
  @DisplayName("Admin getting token tests")
  class AdminTokenTests {
    @Test
    @DisplayName("User does not have admin role - provider requesting")
    void doesNotHaveConsumerRole() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLE_FOR_COS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_ROLE_FOR_COS));
    }

    @Test
    @DisplayName("Cannot get Resource token")
    void noResItemToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_OPEN_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(INVALID_ROLE));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a resource server URL")
    void resServerItemIdNotValidUrl() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a existing RS URL")
    void resServerItemIdNotExist() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(adminRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - no role for RS / user does not own the server")
    void resServerNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      // adminDummy is admin for  DUMMY RS
      given()
          .auth()
          .oauth2(adminDummyRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
    }

    @Test
    @DisplayName("Res Server token success + introspect")
    void resServerTokenSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.ADMIN.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(adminRealRsToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(adminRealRsToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.ADMIN.toString().toLowerCase()))
              .body("iid", equalTo("rs:" + REAL_RS_URL))
              .body("userInfo.email", equalTo(adminRealRsEmail))
              .body("userInfo.name.firstName", not(emptyOrNullString()))
              .body("userInfo.name.lastName", not(emptyOrNullString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }

  @Nested
  @DisplayName("Trustee cannot get any tokens - blocked by OpenAPI")
  class TrusteeTokenTests {
    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.TRUSTEE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(trusteeToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.TRUSTEE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(trusteeToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }

    @Test
    @DisplayName("Cannot get Resource token")
    void noResItemToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_OPEN_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.TRUSTEE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(trusteeToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }

    @Test
    @DisplayName("Cannot get RS token")
    void noResServerToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.TRUSTEE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(trusteeToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }
  }

  @Nested
  @DisplayName("COS admin token")
  class CosAdminTokenTests {
    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken(KcAdminInt kc) {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get Resource token")
    void noResItemToken(KcAdminInt kc) {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_OPEN_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(INVALID_ROLE));
    }

    @Test
    @DisplayName("Cannot get RS token")
    void noResServerToken(KcAdminInt kc) {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_COS_ADMIN_NO_RS))
          .body("detail", equalTo(ERR_COS_ADMIN_NO_RS));
    }

    @Test
    @DisplayName("COS token - does not have COS Admin role - provider calling")
    void cosTokenNotCosAdmin() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("COS token - wrong COS URL")
    void cosTokenNotCosAdmin(KcAdminInt kc) {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_APD_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      given()
          .auth()
          .oauth2(kc.cosAdminToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_COS_URL))
          .body("detail", equalTo(ERR_DETAIL_INVALID_COS_URL));
    }

    @Test
    @DisplayName("Cos success + introspect")
    void cosSuccess(KcAdminInt kc) {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.COS_ADMIN.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(kc.cosAdminToken)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_COS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(kc.cosAdminToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_COS_URL))
              .body("role", equalTo(Roles.COS_ADMIN.toString().toLowerCase()))
              .body("iid", equalTo("cos:" + REAL_COS_URL))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Consumer-delegate getting token tests")
  class ConsumerDelegateTokenTests {
    String delegationIdForConsumerDummyRs;
    String delegationIdForConsumerRealRs;
    String delegateToken;
    String delegateEmail = IntegTestHelpers.email();

    @BeforeAll
    void setup(KcAdminInt kc) {
      delegateToken = kc.createUser(delegateEmail);

      JsonObject consDummyRsReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateEmail)
                              .put("resSerUrl", DUMMY_RS_SERVER)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(consumerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(consDummyRsReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      JsonObject consRealRsReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateEmail)
                              .put("resSerUrl", REAL_RS_URL)
                              .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(consRealRsReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      Response resp =
          given()
              .auth()
              .oauth2(delegateToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .response();

      delegationIdForConsumerDummyRs =
          resp.jsonPath()
              .param("url", DUMMY_RS_SERVER)
              .getString("results.find { it.url == url }.id");
      delegationIdForConsumerRealRs =
          resp.jsonPath().param("url", REAL_RS_URL).getString("results.find { it.url == url }.id");
    }

    @Test
    @DisplayName("User does not have delegate role - provider requesting")
    void doesNotHaveDelegateRole() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("Delegation ID missing")
    void delegationIdMissing() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_DELEGATION_INFO_MISSING))
          .body("detail", equalTo(ERR_DETAIL_DELEGATION_INFO_MISSING));
    }

    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLE_FOR_COS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_ROLE_FOR_COS));
    }

    @Test
    @DisplayName("Res Item - does not exist")
    void resItemDoesNotExist() {
      String badId = UUID.randomUUID().toString();

      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", badId)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ITEMNOTFOUND))
          .body("detail", equalTo(badId));
    }

    @Test
    @DisplayName("Res Item - invalid ID")
    void resItemInvalid() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_INPUT))
          .body("detail", equalTo(INCORRECT_ITEM_ID));
    }

    @Test
    @DisplayName("Res Item - supplied ID not a resource")
    void resItemNotResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_NOT_VALID_RESOURCE))
          .body("detail", equalTo(BRAVO_RES_GROUP_ID.toString()));
    }

    @Test
    @DisplayName("Res Item - delegated RS does not match resource server that item belongs to")
    void resItemNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      // delegation for DUMMY_SERVER will not work since request is for res on REAL_RS
      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerDummyRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS));
    }

    @Test
    @DisplayName("Res Item token APD success without constraints + introspect")
    void resItemApdSuccessNoConstraints() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase())
              .put("context", new JsonObject().put("TestAllow", true));

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForConsumerRealRs)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("iid", equalTo("ri:" + ALPHA_RES_ITEM_SECURE_ID.toString()))
              .body("role", equalTo(Roles.DELEGATE.toString().toLowerCase()))
              .body("rg", equalTo(ALPHA_RES_GROUP_ID.toString()))
              .body("did", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("drl", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }

    @Test
    @DisplayName("Res Item token APD success with constraints + introspect")
    void resItemApdSuccessConstraints() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase())
              .put("context", new JsonObject().put("TestAllowConstraints", true));

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForConsumerRealRs)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.DELEGATE.toString().toLowerCase()))
              .body("iid", equalTo("ri:" + BRAVO_RES_ITEM_SECURE_ID.toString()))
              .body("rg", equalTo(BRAVO_RES_GROUP_ID.toString()))
              .body("did", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("drl", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, false));
    }

    @Test
    @DisplayName("Res Item token APD deny")
    void resItemApdDeny() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_OPEN_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase())
              .put("context", new JsonObject().put("TestDeny", true));

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_APD_EVAL_FAILED));
    }

    @Test
    @DisplayName(
        "Res Item token APD deny needs interaction + introspect - contains delegate in `sub`")
    void resItemApdDenyNeedsInteraction() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase())
              .put("context", new JsonObject().put("TestDenyNInteraction", true));

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForConsumerRealRs)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(403)
              .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
              .body("title", equalTo(ERR_TITLE_APD_INTERACT_REQUIRED))
              .body("detail", equalTo(ERR_DETAIL_APD_INTERACT_REQUIRED))
              .body("context", hasKey("expiry"))
              .body("context", hasKey("link"))
              .body("context.server", equalTo(REAL_APD_URL))
              .extract()
              .path("context.apdToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_APD_URL))
              .body("sid", not(emptyString()))
              .body("link", not(emptyString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a resource server URL")
    void resServerItemIdNotValidUrl() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a existing RS URL")
    void resServerItemIdNotExist() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerRealRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - delegated RS does not match requested RS")
    void resServerNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      // delegation for DUMMY_SERVER will not work since request is for res on REAL_RS
      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForConsumerDummyRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
    }

    @Test
    @DisplayName("Res Server token success + introspect - userInfo contains only delegate info")
    void resServerTokenSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForConsumerRealRs)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.DELEGATE.toString().toLowerCase()))
              .body("iid", equalTo("rs:" + REAL_RS_URL))
              .body("did", equalTo(IntegTestHelpers.getUserIdFromKcToken(consumerRealRsToken)))
              .body("drl", equalTo(Roles.CONSUMER.toString().toLowerCase()))
              .body("userInfo.email", equalTo(delegateEmail))
              .body("userInfo.name.firstName", not(emptyOrNullString()))
              .body("userInfo.name.lastName", not(emptyOrNullString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Provider-delegate getting token tests")
  class ProviderDelegateTokenTests {
    String delegationIdForProviderDummyRs;
    String delegationIdForProviderAlpha;
    String delegationIdForProviderBravo;

    String delegateToken;
    String delegateEmail = IntegTestHelpers.email();

    @BeforeAll
    void setup(KcAdminInt kc) {
      delegateToken = kc.createUser(delegateEmail);

      JsonObject provDummyRsReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateEmail)
                              .put("resSerUrl", DUMMY_RS_SERVER)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerDummyRsToken)
          .contentType(ContentType.JSON)
          .body(provDummyRsReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      JsonObject alphaBravoReq =
          new JsonObject()
              .put(
                  "request",
                  new JsonArray()
                      .add(
                          new JsonObject()
                              .put("userEmail", delegateEmail)
                              .put("resSerUrl", REAL_RS_URL)
                              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given()
          .auth()
          .oauth2(providerAlphaToken)
          .contentType(ContentType.JSON)
          .body(alphaBravoReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      given()
          .auth()
          .oauth2(providerBravoToken)
          .contentType(ContentType.JSON)
          .body(alphaBravoReq.toString())
          .when()
          .post("/delegations")
          .then()
          .statusCode(201)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()));

      Response resp =
          given()
              .auth()
              .oauth2(delegateToken)
              .contentType(ContentType.JSON)
              .when()
              .get("/delegations")
              .then()
              .statusCode(200)
              .extract()
              .response();

      delegationIdForProviderDummyRs =
          resp.jsonPath()
              .param("url", DUMMY_RS_SERVER)
              .getString("results.find { it.url == url }.id");
      delegationIdForProviderAlpha =
          resp.jsonPath()
              .param("url", REAL_RS_URL)
              .param("email", PROVIDER_ALPHA_EMAIL)
              .getString("results.find { it.url == url && it.owner.email == email }.id");
      delegationIdForProviderBravo =
          resp.jsonPath()
              .param("url", REAL_RS_URL)
              .param("email", PROVIDER_BRAVO_EMAIL)
              .getString("results.find { it.url == url && it.owner.email == email }.id");
    }

    @Test
    @DisplayName("User does not have delegate role - consumer requesting")
    void doesNotHaveConsumerRole() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", DUMMY_RS_SERVER)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(consumerRealRsToken)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_ROLE_NOT_OWNED))
          .body("detail", equalTo(ERR_DETAIL_ROLE_NOT_OWNED));
    }

    @Test
    @DisplayName("Cannot get Res group token")
    void noResGrpToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_GROUP.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_NO_RES_GRP_TOKEN))
          .body("detail", equalTo(ERR_DETAIL_NO_RES_GRP_TOKEN));
    }

    @Test
    @DisplayName("Cannot get COS token")
    void noCosToken() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_COS_URL)
              .put("itemType", ItemType.COS.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_ROLE_FOR_COS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_ROLE_FOR_COS));
    }

    @Test
    @DisplayName("Res Item - does not exist")
    void resItemDoesNotExist() {
      String badId = UUID.randomUUID().toString();

      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", badId)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ITEMNOTFOUND))
          .body("detail", equalTo(badId));
    }

    @Test
    @DisplayName("Res Item - invalid ID")
    void resItemInvalid() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(INVALID_INPUT))
          .body("detail", equalTo(INCORRECT_ITEM_ID));
    }

    @Test
    @DisplayName("Res Item - supplied ID not a resource")
    void resItemNotResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_NOT_VALID_RESOURCE))
          .body("detail", equalTo(BRAVO_RES_GROUP_ID.toString()));
    }

    @Test
    @DisplayName("Res Item - delegated RS does not match resource server that item belongs to")
    void resItemNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      // delegation for DUMMY_SERVER will not work since request is for res on REAL_RS
      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderDummyRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS));
    }

    @Test
    @DisplayName("Res Item - does not have own resource")
    void resItemNotOwnResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      // Delegation for Alpha provider will not work - Alpha provider does not own resource, belongs
      // to Bravo provider
      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(NOT_RES_OWNER));
    }

    @Test
    @DisplayName("Res Item - cannot access PII resource")
    void resItemNoAccessToPiiResource() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_ITEM_PII_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderBravo)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES));
    }

    @Test
    @DisplayName("Res Item success + introspect")
    void resItemSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", ALPHA_RES_ITEM_SECURE_ID)
              .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForProviderAlpha)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("role", equalTo(Roles.DELEGATE.toString().toLowerCase()))
              .body("iid", equalTo("ri:" + ALPHA_RES_ITEM_SECURE_ID.toString()))
              .body("rg", equalTo(ALPHA_RES_GROUP_ID.toString()))
              .body("did", equalTo(IntegTestHelpers.getUserIdFromKcToken(providerAlphaToken)))
              .body("drl", equalTo(Roles.PROVIDER.toString().toLowerCase()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a resource server URL")
    void resServerItemIdNotValidUrl() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", BRAVO_RES_GROUP_ID)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - supplied item ID not a existing RS URL")
    void resServerItemIdNotExist() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderAlpha)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_RS))
          .body("detail", equalTo(ERR_DETAIL_INVALID_RS));
    }

    @Test
    @DisplayName("Res Server - delegated RS does not match requested RS")
    void resServerNoRoleForRs() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      // delegation for DUMMY_SERVER will not work since request is for res on REAL_RS
      given()
          .auth()
          .oauth2(delegateToken)
          .header("delegationId", delegationIdForProviderDummyRs)
          .contentType(ContentType.JSON)
          .body(tokenReq.toString())
          .when()
          .post("/token")
          .then()
          .statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ACCESS_DENIED))
          .body("detail", equalTo(ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
    }

    @Test
    @DisplayName("Res Server token success + introspect - userInfo contains only delegate info")
    void resServerTokenSuccess() {
      JsonObject tokenReq =
          new JsonObject()
              .put("itemId", REAL_RS_URL)
              .put("itemType", ItemType.RESOURCE_SERVER.toString().toLowerCase())
              .put("role", Roles.DELEGATE.toString().toLowerCase());

      String token =
          given()
              .auth()
              .oauth2(delegateToken)
              .header("delegationId", delegationIdForProviderBravo)
              .contentType(ContentType.JSON)
              .body(tokenReq.toString())
              .when()
              .post("/token")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_SUCCESS))
              .body("results", hasKey("expiry"))
              .body("results.server", equalTo(REAL_RS_URL))
              .extract()
              .path("results.accessToken");

      String introspectResp =
          given()
              .contentType(ContentType.JSON)
              .body(new JsonObject().put("accessToken", token).toString())
              .when()
              .post("/introspect")
              .then()
              .statusCode(200)
              .body("type", equalTo(Urn.URN_SUCCESS.toString()))
              .body("title", equalTo(TOKEN_AUTHENTICATED))
              .rootPath("results")
              .body("sub", equalTo(IntegTestHelpers.getUserIdFromKcToken(delegateToken)))
              .body("iss", equalTo(REAL_COS_URL))
              .body("aud", equalTo(REAL_RS_URL))
              .body("iid", equalTo("rs:" + REAL_RS_URL))
              .body("role", equalTo(Roles.DELEGATE.toString().toLowerCase()))
              .body("did", equalTo(IntegTestHelpers.getUserIdFromKcToken(providerBravoToken)))
              .body("drl", equalTo(Roles.PROVIDER.toString().toLowerCase()))
              .body("userInfo.email", equalTo(delegateEmail))
              .body("userInfo.name.firstName", not(emptyOrNullString()))
              .body("userInfo.name.lastName", not(emptyOrNullString()))
              .extract()
              .asString();

      assertTrue(validateExpAndIat(introspectResp));
      assertTrue(validateCons(introspectResp, true));
    }
  }
}
