package iudx.aaa.server.apd;

import org.junit.jupiter.api.extension.ExtendWith;
import static io.restassured.RestAssured.*;
import static iudx.aaa.server.apd.Constants.*;
import static iudx.aaa.server.token.Constants.*;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static org.hamcrest.Matchers.*;
import java.util.List;
import java.util.Map;
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
import iudx.aaa.server.apiserver.ApdStatus;
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

  private static String DUMMY_SERVER = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

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

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).when().post("/apd").then().statusCode(400)
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
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase())).and()
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
  
  @Test
  @DisplayName("No roles cannot get APD")
  void noRolesViewApd()
  {
    given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(404)
          .body("type", equalTo(Urn.URN_MISSING_INFO.toString()))
          .body("title", equalTo(ERR_TITLE_NO_APPROVED_ROLES.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_APPROVED_ROLES.toString()));
  }
  
  @Test
  @DisplayName("Update APD : No token")
  void updateApdNoToken()
  {
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
              ApdStatus.ACTIVE.toString().toLowerCase())));
      
    given().contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(401)
          .body("type", equalTo(Urn.URN_MISSING_AUTH_TOKEN.toString()));
  }
  
  @Test
  @DisplayName("Update APD : No roles cannot update APD status")
  void noRolesUpdateApd()
  {
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
              ApdStatus.ACTIVE.toString().toLowerCase())));
      
    given().auth().oauth2(tokenNoRoles).contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE.toString()));
  }
  
  @Test
  @DisplayName("Update APD : All roles - except COS Admin - cannot update APD status")
  void allRolesUpdateApd()
  {
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
              ApdStatus.ACTIVE.toString().toLowerCase())));
      
    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(401)
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString()))
          .body("title", equalTo(ERR_TITLE_NO_COS_ADMIN_ROLE.toString()))
          .body("detail", equalTo(ERR_DETAIL_NO_COS_ADMIN_ROLE.toString()));
  }
  
  @Test
  @DisplayName("Update APD : Empty body")
  void updateApdMissingBody(KcAdminInt kc)
  {
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).when()
          .put("/apd").then().statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }
  
  @Test
  @DisplayName("Update APD : Duplicate reqs")
  void updateApdDuplicates(KcAdminInt kc)
  {
    JsonObject req = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );
      
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }
  
  @Test
  @DisplayName("Update APD : Non-existent APD ID")
  void updateApdNonExistentId(KcAdminInt kc)
  {
    String randId = UUID.randomUUID().toString();
    JsonObject req = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", randId).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
      
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_APDID.toString()))
          .body("detail", equalTo(randId));
  }
  
  @Test
  @DisplayName("Update APD : Same ID, different statuses")
  void updateApdSameIdDifferentStatuses(KcAdminInt kc)
  {
    String offendingId = UUID.randomUUID().toString();
    JsonObject req = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", offendingId).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", offendingId).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
      
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(req.toString()).when()
          .put("/apd").then().statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_DUPLICATE_REQ.toString()))
          .body("detail", equalTo(offendingId));
  }
  
  @Test
  @DisplayName("Update APD : Missing keys")
  void updateApdMissingKeys(KcAdminInt kc)
  { 
    JsonObject noId = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );
    
    JsonObject noStatus = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()))
        );
    
    JsonObject noRequest = 
        new JsonObject().put("id", UUID.randomUUID().toString())
            .put("status",
            ApdStatus.ACTIVE.toString().toLowerCase());
      
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(noId.toString())
        .when().put("/apd").then().statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(noStatus.toString())
        .when().put("/apd").then().statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(noRequest.toString())
        .when().put("/apd").then().statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }
  
  @Test
  @DisplayName("Update APD : Invalid keys")
  void updateApdInvalidKeys(KcAdminInt kc)
  { 
    JsonObject invalidId = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", RandomStringUtils.randomAlphabetic(10)).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );

    JsonObject invalidStatus = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()).put("status",
            "pending"))
        );
      
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(invalidId.toString())
        .when().put("/apd").then().statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(invalidStatus.toString())
        .when().put("/apd").then().statusCode(400)
        .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
  }
  
  @Nested
  @DisplayName("Lifecycle of changing APD status with multiple requests")
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  class ApdStateChangingFlow {
    String serverIdAlpha;
    String serverIdBravo;
    String serverNameAlpha = RandomStringUtils.randomAlphabetic(10);
    String serverUrlAlpha = serverNameAlpha + ".com";
    
    String serverNameBravo = RandomStringUtils.randomAlphabetic(10);
    String serverUrlBravo = serverNameBravo + ".com";
    
    String email = IntegTestHelpers.email();
    
    @BeforeAll
    void setup(KcAdminInt kc)
    {
      kc.createUser(email);
      JsonObject body = new JsonObject().put("name", serverNameAlpha).put("url", serverUrlAlpha).put("owner",
          email);

      serverIdAlpha = given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(body.toString()).when().post("/apd").then()
          .statusCode(201).extract().path("results.id");
      
      body = new JsonObject().put("name", serverNameBravo).put("url", serverUrlBravo).put("owner",
          email);

      serverIdBravo = given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(body.toString()).when().post("/apd").then()
          .statusCode(201).extract().path("results.id");
    }
    
    @Test
    @Order(1)
    @DisplayName("Deactivate APD Alpha successfully")
    void deactivateApdAlpha(KcAdminInt kc)
    {
      
    JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdAlpha).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
    
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_UPDATED_APD))
          .body("results[0]", hasKey("id"))
          .rootPath("results[0]")
          .body("name", equalTo(serverNameAlpha))
          .body("url", equalTo(serverUrlAlpha.toLowerCase()))
          .body("status", equalTo(ApdStatus.INACTIVE.toString().toLowerCase()));
    }
    
    @Test
    @Order(2)
    @DisplayName("Deactivating APD Alpha again - fail")
    void deactivateApdAlphaAgainFail(KcAdminInt kc)
    {
      
    JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdAlpha).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
    
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_CANT_CHANGE_APD_STATUS.toString()))
          .body("detail", equalTo(serverIdAlpha));
    }
    
    @Test
    @Order(3)
    @DisplayName("Random ID in multiple request - trying to deactivate Server Bravo")
    void multipleReqRandId(KcAdminInt kc)
    {
     String randId = UUID.randomUUID().toString(); 
     JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", randId).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
    
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(400)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_INVALID_APDID.toString()))
          .body("detail", equalTo(randId));
    }
    
    @Test
    @Order(4)
    @DisplayName("Trying to activate Server Bravo (wrong) and activate Server Alpha (correct) - fails")
    void multipleReqWrongState(KcAdminInt kc)
    {
     JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdAlpha).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );
    
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_CANT_CHANGE_APD_STATUS.toString()))
          .body("detail", equalTo(serverIdBravo));
    }
    
    @Test
    @Order(5)
    @DisplayName("Trying to deactivate Server Bravo and activate Server Alpha - succeeds")
    void multipleReqSuccess(KcAdminInt kc)
    {
      JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdAlpha).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
    
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_UPDATED_APD)).and()
          .body("results.find {it.id == '%s'}.url", withArgs(serverIdAlpha),
              equalTo(serverUrlAlpha.toLowerCase()))
          .body("results.find {it.id == '%s'}.status", withArgs(serverIdAlpha),
              equalTo(ApdStatus.ACTIVE.toString().toLowerCase()))
          .body("results.find {it.id == '%s'}.url", withArgs(serverIdBravo),
              equalTo(serverUrlBravo.toLowerCase()))
          .body("results.find {it.id == '%s'}.status", withArgs(serverIdBravo),
              equalTo(ApdStatus.INACTIVE.toString().toLowerCase()));
    }
    
    @Test
    @Order(6)
    @DisplayName("Same multiple req again - fails")
    void multipleReqRepeatedFail(KcAdminInt kc)
    {
      JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdAlpha).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        .add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.INACTIVE.toString().toLowerCase()))
        );
    
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_CANT_CHANGE_APD_STATUS.toString()));
    }
    
    @Test
    @Order(7)
    @DisplayName("Activate Server Bravo successfully")
    void activateApdAlpha(KcAdminInt kc)
    {
      
    JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );
    
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_UPDATED_APD))
          .body("results[0]", hasKey("id"))
          .rootPath("results[0]")
          .body("name", equalTo(serverNameBravo))
          .body("url", equalTo(serverUrlBravo.toLowerCase()))
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase()));
    }
    
    @Test
    @Order(8)
    @DisplayName("Activating Server Bracvo again - fail")
    void activateApdBravoAgainFail(KcAdminInt kc)
    {
      
    JsonObject body = new JsonObject().put("request",
        new JsonArray().add(new JsonObject().put("id", serverIdBravo).put("status",
            ApdStatus.ACTIVE.toString().toLowerCase()))
        );
    
    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(body.toString()).when()
          .put("/apd").then().statusCode(403)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_CANT_CHANGE_APD_STATUS.toString()))
          .body("detail", equalTo(serverIdBravo));
    }
  }
  
  @Nested
  @DisplayName("Lifecycle of trustee user as APD is created, activated and deactivated")
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  class ApdAndTrusteeFlow {
    
    String serverId;
    String email = IntegTestHelpers.email();
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";
    
    String trusteeToken;
    
    String trusteeClientId;
    String trusteeClientSecret;

    @BeforeAll
    void setup(KcAdminInt kc)
    {
      trusteeToken = kc.createUser(email);
      JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));
    
      given().auth().oauth2(trusteeToken).contentType(ContentType.JSON).body(consReq.toString()).when()
        .post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer role to trustee to get client creds", is(200)));
      
      String clientInfo = given().auth().oauth2(trusteeToken).contentType(ContentType.JSON).when()
          .get("/user/clientcredentials").then()
          .statusCode(201)
          .extract().response().asString();
      
      trusteeClientId =
          new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
      trusteeClientSecret =
          new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);
    }
    
    @Test
    @Order(1)
    @DisplayName("User going to be trustee cannot call search user and get delegate emails APIs")
    void userCannotCallSearchAndGetDelegEmailApis()
    {
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
      
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/delegations/emails").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
    }
    
    @Test
    @Order(2)
    @DisplayName("User going to be trustee does not have trustee role (has consumer role, so won't get 404)")
    void userDoesNotHaveTrusteeRole()
    {
      given().auth().oauth2(trusteeToken).when()
          .get("/user/roles").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("results.roles", not(hasItem(Roles.TRUSTEE.toString().toLowerCase())))
          .body("results.rolesToRsMapping.trustee", is(nullValue()));
    }
    
    @Test
    @Order(3)
    @DisplayName("Create APD success")
    void createApd(KcAdminInt kc) {
      
      JsonObject body = new JsonObject().put("name", serverName).put("url", serverUrl).put("owner",
          email);

      serverId = given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(body.toString()).when().post("/apd").then()
          .statusCode(201).extract().path("results.id");
    }
    
    @Test
    @Order(4)
    @DisplayName("Trustee can call search user and get delegate emails APIs")
    void trusteeCanCallSearchAndGetDelegEmailApis()
    {
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then()
          .statusCode(not(401));
      
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/delegations/emails").then()
          .statusCode(not(401));
    }
    
    @Test
    @Order(5)
    @DisplayName("User has trustee role")
    void userHasTrusteeRole()
    {
      given().auth().oauth2(trusteeToken).when()
          .get("/user/roles").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("results.roles", hasItem(Roles.TRUSTEE.toString().toLowerCase()))
          .body("results.rolesToRsMapping.trustee", hasItem(serverUrl.toLowerCase()));
    }
    
    @Test
    @Order(6)
    @DisplayName("Deactivating APD")
    void deactiveApd(KcAdminInt kc) {
      
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", serverId).put("status",
              ApdStatus.INACTIVE.toString().toLowerCase())));

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(req.toString()).when().put("/apd").then()
          .statusCode(200).extract().path("results.id");
    }
    
    @Test
    @Order(7)
    @DisplayName("User can't use the search, deleg APIs after deactivating")
    void userCannotCallSearchAndGetDelegEmailApisAfterDeactive()
    {
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
      
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/delegations/emails").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
    }
    
    @Test
    @Order(8)
    @DisplayName("User loses trustee role after deactivating")
    void userDoesNotHaveTrusteeRoleAfterDeactivate()
    {
      given().auth().oauth2(trusteeToken).when()
          .get("/user/roles").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("results.roles", not(hasItem(Roles.TRUSTEE.toString().toLowerCase())))
          .body("results.rolesToRsMapping.trustee", is(nullValue()));
    }
    
    @Test
    @Order(9)
    @DisplayName("Activating APD again")
    void activateApdAgain(KcAdminInt kc) {
      
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", serverId).put("status",
              ApdStatus.ACTIVE.toString().toLowerCase())));

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(req.toString()).when().put("/apd").then()
          .statusCode(200);
    }
    
    @Test
    @Order(10)
    @DisplayName("User gets back trustee role and API access")
    void userGetsBackTrusteeRole()
    {
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then()
          .statusCode(not(401));
      
      given().when().headers(Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret))
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/delegations/emails").then()
          .statusCode(not(401));

      given().auth().oauth2(trusteeToken).when()
          .get("/user/roles").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("results.roles", hasItem(Roles.TRUSTEE.toString().toLowerCase()))
          .body("results.rolesToRsMapping.trustee", hasItem(serverUrl.toLowerCase()));
    }
    
  }
  
  @Nested
  @DisplayName("Lifecycle of viewing APD when created and made active, inactive and then active")
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  class GetApd {

    String serverId;
    String email = IntegTestHelpers.email();
    String serverName = RandomStringUtils.randomAlphabetic(10);
    String serverUrl = serverName + ".com";

    @BeforeAll
    void setup(KcAdminInt kc)
    {
      kc.createUser(email);
    }
    
    @Test
    @Order(1)
    @DisplayName("No one cannot see APD before created")
    void noOneCanSeeApdBeforeExists(KcAdminInt kc)
    {
      
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results", hasSize(greaterThan(0)))
          .body("results.find { it.url == '%s' }", withArgs(serverUrl), is(nullValue()));
      
      given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results.find { it.url == '%s' }", withArgs(serverUrl), is(nullValue()));
    }
    
    @Test
    @Order(2)
    @DisplayName("Create APD success")
    void createApd(KcAdminInt kc) {
      
      JsonObject body = new JsonObject().put("name", serverName).put("url", serverUrl).put("owner",
          email);

      serverId = given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(body.toString()).when().post("/apd").then()
          .statusCode(201).extract().path("results.id");
    }
    
    @Test
    @Order(3)
    @DisplayName("COS Admin can get APD")
    void cosAdminViewApd(KcAdminInt kc)
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
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase())).and()
          .body("", hasKey("owner")).and()
          .body("owner", hasKey("id")).and()
          .body("owner", hasKey("name")).and()
          .body("owner.name", hasKey("firstName")).and()
          .body("owner.name", hasKey("lastName")).and()
          .body("owner.email", equalTo(email.toLowerCase()));
    }
    
    @Test
    @Order(4)
    @DisplayName("All roles can get APD")
    void rolesViewApd()
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
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase())).and()
          .body("owner", hasKey("id")).and()
          .body("owner", hasKey("name")).and()
          .body("owner.name", hasKey("firstName")).and()
          .body("owner.name", hasKey("lastName")).and()
          .body("owner.email", equalTo(email.toLowerCase()));
    }
    
    @Test
    @Order(5)
    @DisplayName("Deactivating APD")
    void deactiveApd(KcAdminInt kc) {
      
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", serverId).put("status",
              ApdStatus.INACTIVE.toString().toLowerCase())));

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(req.toString()).when().put("/apd").then()
          .statusCode(200);
    }
    
    @Test
    @Order(6)
    @DisplayName("COS Admin can see inactive APD")
    void cosAdminViewInactiveApd(KcAdminInt kc)
    {
      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results", hasSize(greaterThan(0)))
          .rootPath("results.find { it.id == '%s' }", withArgs(serverId))
          .body("", hasKey("id"))
          .body("name", equalTo(serverName)).and()
          .body("status", equalTo(ApdStatus.INACTIVE.toString().toLowerCase())).and()
          .body("url", equalTo(serverUrl.toLowerCase())).and()
          .body("", hasKey("owner"));
    }
    
    @Test
    @Order(7)
    @DisplayName("All roles cannot get inactive APD")
    void rolesCannotViewInactiveApd()
    {
      given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
          .get("/apd").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString())).and()
          .body("title", equalTo(SUCC_TITLE_APD_READ))
          .body("results.find { it.id == '%s' }", withArgs(serverId), is(nullValue()));
    }
    
    @Test
    @Order(8)
    @DisplayName("Activating APD")
    void activateApdAgain(KcAdminInt kc) {
      
      JsonObject req = new JsonObject().put("request",
          new JsonArray().add(new JsonObject().put("id", serverId).put("status",
              ApdStatus.ACTIVE.toString().toLowerCase())));

      given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON)
          .body(req.toString()).when().put("/apd").then()
          .statusCode(200);
    }
    
    @Test
    @Order(9)
    @DisplayName("Both COS Admin and all roles can get activated APD")
    void cosAdminAndRolesViewReactivatedApd(KcAdminInt kc)
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
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase()));

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
          .body("status", equalTo(ApdStatus.ACTIVE.toString().toLowerCase()));
    } 
  }
}
