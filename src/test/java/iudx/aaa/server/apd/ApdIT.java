package iudx.aaa.server.apd;

import org.junit.jupiter.api.extension.ExtendWith;
import static io.restassured.RestAssured.*;
import static iudx.aaa.server.apd.Constants.*;
import static iudx.aaa.server.token.Constants.*;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static org.hamcrest.Matchers.*;
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
public class ApdIT {

  /**
   * Token with consumer, provider, admin, trustee and delegate roles.
   */
  private static String tokenRoles;
  
  /**
   * Token w/ no roles.
   */
  private static String tokenNoRoles;

  @BeforeAll
  static void setup(KcAdminInt kc) {
    baseURI = "http://localhost";
    port = 8443;
    basePath = "/auth/v1";

    String DUMMY_SERVER = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    tokenNoRoles = kc.createUser(IntegTestHelpers.email());

    String email = IntegTestHelpers.email();
    tokenRoles = kc.createUser(email);

    // create RS
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER).put("owner", email);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(rsReq.toString())
        .when().post("/admin/resourceservers").then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));

    // create consumer, provider roles
    JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER))
        .put("provider", new JsonArray().add(DUMMY_SERVER));
    
    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(consReq.toString()).when()
        .post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer, provider", is(200)));
    
    String providerRegId =  
    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
        .get("/admin/provider/registrations").then()
        .statusCode(describedAs("Setup - Get provider reg. ID", is(200))).extract().path("results[0].id");
    
    JsonObject approveReq = new JsonObject().put("request", new JsonArray().add(new JsonObject()
        .put("status", RoleStatus.APPROVED.toString().toLowerCase()).put("id", providerRegId)));

    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(approveReq.toString()).when()
        .put("/admin/provider/registrations").then()
        .statusCode(describedAs("Setup - Approve provider", is(200)));
    
    // create APD
    JsonObject apdReq =
        new JsonObject().put("name", "name").put("url", "apd" + DUMMY_SERVER).put("owner", email);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(apdReq.toString()).when()
        .post("/apd").then()
        .statusCode(describedAs("Setup - Created dummy APD", is(201)));

    // create delegation
    String delegatorEmail = IntegTestHelpers.email();
    String delegatorTok = kc.createUser(delegatorEmail);

    JsonObject delegatorConsReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));
    
    given().auth().oauth2(delegatorTok).contentType(ContentType.JSON)
        .body(delegatorConsReq.toString()).when().post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer delegaTOR", is(200)));

    JsonObject delegReq =
        new JsonObject().put("request", new JsonArray().add(new JsonObject().put("userEmail", email)
            .put("resSerUrl", DUMMY_SERVER).put("role", Roles.CONSUMER.toString().toLowerCase())));

    given().auth().oauth2(delegatorTok).contentType(ContentType.JSON).body(delegReq.toString()).when()
        .post("/delegations").then()
        .statusCode(describedAs("Setup - Created delegation", is(201)));
  }
  
  @Test
  @DisplayName("Create APD - No token sent")
  void createApdNoToken(KcAdminInt kc) {
    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "some-email@gmail.com");

    given().contentType(ContentType.JSON).body(body.toString()).when()
        .post("/apd").then().statusCode(401).and()
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }

  @Test
  @DisplayName("Create APD - User without roles cannot call API")
  void createApdNoRoles(KcAdminInt kc) {
    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "some-email@gmail.com");

    given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).body(body.toString()).when()
        .post("/apd").then().statusCode(401).and()
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
        .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE)).and()
        .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE));
  }

  @Test
  @DisplayName("Create APD - User with consumer, provider, admin, delegate, trustee roles cannot call API")
  void createApdRolesNotAllowed(KcAdminInt kc) {
    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "some-email@gmail.com");

    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(body.toString()).when()
        .post("/apd").then().statusCode(401).and()
        .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
        .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE)).and()
        .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE));
  }

  @Test
  @DisplayName("Create APD - Empty body")
  void createApdEmptyBody(KcAdminInt kc) {

    given().auth().oauth2(kc.cosAdminToken).when().post("/apd").then().statusCode(400)
        .and().body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create APD - Missing keys")
  void createApdMissingKeys(KcAdminInt kc) {

    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "some-email@gmail.com");

    JsonObject missingName = body.copy();
    missingName.remove("name");
    JsonObject missingUrl = body.copy();
    missingUrl.remove("url");
    JsonObject missingOwner = body.copy();
    missingOwner.remove("owner");

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(missingName.toString()).when()
        .post("/apd").then().statusCode(400).and()
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(missingOwner.toString()).when()
        .post("/apd").then().statusCode(400).and()
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(missingUrl.toString()).when()
        .post("/apd").then().statusCode(400).and()
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create APD - Invalid email")
  void createApdInvalidEmail(KcAdminInt kc) {

    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "some-emailsasd12jdnamdgmail.com");

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
        .post("/apd").then().statusCode(400).and()
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }

  @Test
  @DisplayName("Create APD - Invalid URLs - caught by OpenAPI")
  void createApdInvalidUrlOAS(KcAdminInt kc) {

    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "someemail@gmail.com");

    List<String> invalidUrls = List.of("https://url.com", "url.com/path");

    invalidUrls.forEach(url -> {
      body.put("url", url);

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    });
  }

  @Test
  @DisplayName("Create APD - Invalid URLs - caught by Guava lib")
  void createApdInvalidUrlGuava(KcAdminInt kc) {

    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        "someemail@gmail.com");

    List<String> invalidUrls = List.of("1url.1.2.3.3", "url....213da3123");

    invalidUrls.forEach(url -> {
      body.put("url", url);

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString())).and()
          .body("title", equalTo(ERR_TITLE_INVALID_DOMAIN)).and()
          .body("detail", equalTo(ERR_DETAIL_INVALID_DOMAIN));
    });
  }
  
  @Test
  @DisplayName("Create APD - Email not registered on UAC")
  void createApdEmailNotOnUac(KcAdminInt kc) {

    String badEmail = RandomStringUtils.randomAlphabetic(15) +"@gmail.com";
    JsonObject body = new JsonObject().put("name", "name").put("url", "url.com").put("owner",
        badEmail);

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString())).and()
          .body("title", equalTo(ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK)).and()
          .body("detail", stringContainsInOrder(badEmail.toLowerCase()));
  }
  
  @Test
  @DisplayName("Create APD - Successfully created APD")
  void createApdSuccess(KcAdminInt kc) {

    String email = IntegTestHelpers.email();
    kc.createUser(email);
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";
    
    JsonObject body = new JsonObject().put("name", serverName).put("url", serverUrl).put("owner",
        email);

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(201).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_REGISTERED_APD)).and()
          .body("results", hasKey("id"))
          .body("results", hasKey("owner"))
          .rootPath("results")
          .body("name", equalTo(serverName)).and()
          .body("url", equalTo(serverUrl.toLowerCase())).and()
          .body("owner", hasKey("id")).and()
          .body("owner", hasKey("name")).and()
          .body("owner.name", hasKey("firstName")).and()
          .body("owner.name", hasKey("lastName")).and()
          .body("owner.email", equalTo(email.toLowerCase()));
  }
  
  @Test
  @DisplayName("Create APD - Duplicate APD")
  void createApdDuplicate(KcAdminInt kc) {

    String email = IntegTestHelpers.email();
    kc.createUser(email);
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";
    
    JsonObject body = new JsonObject().put("name", serverName).put("url", serverUrl).put("owner",
        email);

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(201).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_REGISTERED_APD));

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .post("/apd").then().statusCode(409).and()
          .body("type", equalTo(Urn.URN_ALREADY_EXISTS.toString())).and()
          .body("title", equalTo(ERR_TITLE_EXISTING_DOMAIN)).and()
          .body("detail", equalTo(ERR_DETAIL_EXISTING_DOMAIN));
  }
  
  @Test
  @DisplayName("Get APD - No token sent")
  void getRsNoToken(KcAdminInt kc) {

    when()
        .get("/apd").then().statusCode(401).and()
        .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }
  
  @Nested
  @DisplayName("Get APD - Any user with valid token can call API")
  @TestInstance(Lifecycle.PER_CLASS)
  class GetRsSuccess {

    String serverId;
    String email = IntegTestHelpers.email();
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";

    @BeforeAll
    void createApd(KcAdminInt kc) {
      kc.createUser(email);
      
      JsonObject body = new JsonObject().put("name", serverName).put("url", serverUrl).put("owner",
          email);

      serverId = given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(body.toString()).when().post("/apd").then()
          .statusCode(describedAs("Created RS for Get resource server", is(201))).extract()
          .path("results.id");
    }
    
    @Test
    @DisplayName("COS Admin can get RS")
    void cosAdminViewRs(KcAdminInt kc)
    {
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName)).and()
          .body("url", equalTo(serverUrl.toLowerCase())).and()
          .body("", hasKey("owner")).and()
          .body("owner", hasKey("id")).and()
          .body("owner", hasKey("name")).and()
          .body("owner.name", hasKey("firstName")).and()
          .body("owner.name", hasKey("lastName")).and()
          .body("owner.email", equalTo(email.toLowerCase()));
    }
    
    @Test
    @DisplayName("All roles can get APD")
    void rolesViewRs()
    {
      given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName)).and()
          .body("url", equalTo(serverUrl.toLowerCase())).and()
          .body("", hasKey("owner")).and()
          .body("owner", hasKey("id")).and()
          .body("owner", hasKey("name")).and()
          .body("owner.name", hasKey("firstName")).and()
          .body("owner.name", hasKey("lastName")).and()
          .body("owner.email", equalTo(email.toLowerCase()));
    }
    
    @Test
    @DisplayName("No roles cannot get APD")
    void noRolesViewRs()
    {
      given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(404).and()
          .statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
    }
  }
}
