package iudx.aaa.server.apd;

import org.junit.jupiter.api.extension.ExtendWith;
import static io.restassured.RestAssured.*;
import static iudx.aaa.server.apd.Constants.*;
import static iudx.aaa.server.token.Constants.*;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.*;
import static iudx.aaa.server.policy.Constants.*;
import static org.hamcrest.Matchers.*;
import java.util.Base64;
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
public class TrusteeOperationsIT {

  /**
   * Details of user w/ consumer, provider, admin and delegate roles.
   */
  private static Map<String, String> rolesClientCreds;
  private static String tokenRoles;

  /**
   * Details of user w/ trustee role of a valid APD.
   */
  private static Map<String, String> trusteeClientCreds;
  
  private static String noRolesUserId;
  private static String noRolesEmail;
      
  private static String DUMMY_SERVER = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static String DUMMY_SERVER_NO_ONE_HAS_ROLES = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  
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

    String rolesEmail = IntegTestHelpers.email();
    String trusteeEmail = IntegTestHelpers.email();
    noRolesEmail = IntegTestHelpers.email();
    
    tokenRoles = kc.createUser(rolesEmail);
    String trusteeToken = kc.createUser(trusteeEmail);
    String noRolesToken = kc.createUser(noRolesEmail);
    
    // getting user ID from Keycloak JWT payload
    noRolesUserId = IntegTestHelpers.getUserIdFromKcToken(noRolesToken);

    // create RS
    JsonObject rsReq =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER).put("owner", rolesEmail);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(rsReq.toString())
        .when().post("/admin/resourceservers").then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));
    
    // create RS
    JsonObject rsReq2 =
        new JsonObject().put("name", "name").put("url", DUMMY_SERVER_NO_ONE_HAS_ROLES).put("owner", rolesEmail);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(rsReq2.toString())
        .when().post("/admin/resourceservers").then()
        .statusCode(describedAs("Setup - Created dummy RS", is(201)));

    // create consumer, provider roles
    JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER))
        .put("provider", new JsonArray().add(DUMMY_SERVER));
    
    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(consReq.toString()).when()
        .post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer, provider", is(200))).extract().path("results.userId");
    
    String providerRegId =  
    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
        .get("/admin/provider/registrations").then()
        .statusCode(describedAs("Setup - Get provider reg. ID", is(200))).extract().path("results[0].id");
    
    JsonObject approveReq = new JsonObject().put("request", new JsonArray().add(new JsonObject()
        .put("status", RoleStatus.APPROVED.toString().toLowerCase()).put("id", providerRegId)));

    given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(approveReq.toString()).when()
        .put("/admin/provider/registrations").then()
        .statusCode(describedAs("Setup - Approve provider", is(200)));
    
    // create delegation
    String delegatorEmail = IntegTestHelpers.email();
    String delegatorTok = kc.createUser(delegatorEmail);

    JsonObject delegatorConsReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));
    
    given().auth().oauth2(delegatorTok).contentType(ContentType.JSON)
        .body(delegatorConsReq.toString()).when().post("/user/roles").then()
        .statusCode(describedAs("Setup - Added consumer delegaTOR", is(200)));

    JsonObject delegReq =
        new JsonObject().put("request", new JsonArray().add(new JsonObject().put("userEmail", rolesEmail)
            .put("resSerUrl", DUMMY_SERVER).put("role", Roles.CONSUMER.toString().toLowerCase())));

    given().auth().oauth2(delegatorTok).contentType(ContentType.JSON).body(delegReq.toString()).when()
        .post("/delegations").then()
        .statusCode(describedAs("Setup - Created delegation", is(201)));
    
    // create APD
    JsonObject apdReq =
        new JsonObject().put("name", "name").put("url", "apd" + DUMMY_SERVER).put("owner", trusteeEmail);

    given().auth().oauth2(kc.cosAdminToken).contentType(ContentType.JSON).body(apdReq.toString()).when()
        .post("/apd").then()
        .statusCode(describedAs("Setup - Created dummy APD", is(201)));
    
    // get client creds for both sets of users
    String clientInfo = given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
        .get("/user/clientcredentials").then()
        .statusCode(201)
        .extract().response().asString();

    String rolesClientId = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_ID);
    String rolesClientSecret = new JsonObject(clientInfo).getJsonObject("results").getString(RESP_CLIENT_SC);
    
    rolesClientCreds = Map.of("clientId", rolesClientId, "clientSecret", rolesClientSecret);
    
    String clientInfoTrustee = given().auth().oauth2(trusteeToken).contentType(ContentType.JSON).when()
        .get("/user/clientcredentials").then()
        .statusCode(201)
        .extract().response().asString();

    String trusteeClientId = new JsonObject(clientInfoTrustee).getJsonObject("results").getString(RESP_CLIENT_ID);
    String trusteeClientSecret = new JsonObject(clientInfoTrustee).getJsonObject("results").getString(RESP_CLIENT_SC);
    
    trusteeClientCreds = Map.of("clientId", trusteeClientId, "clientSecret", trusteeClientSecret);
  }
  
  @Test
  @DisplayName("Consumer, provider, admin, delegate cannot call search user")
  void rolesCannotCallSearch()
  {
      given().when().headers(rolesClientCreds)
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
      
      given().when().headers(rolesClientCreds)
          .queryParams(Map.of("email", RandomStringUtils.randomAlphabetic(10) + "@gmail.com",
              "resourceServer", DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/user/search").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
    }
    
    @Test
    @DisplayName("Search user - Missing and extra params")
    void searchUserMissingAndExtraParams()
    {
      Map<String, String> queryParams = Map.of("email", RandomStringUtils.random(10) + "@gmail.com", "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase(), "userId", UUID.randomUUID().toString());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("email", RandomStringUtils.random(10) + "@gmail.com", "resourceServer",
              DUMMY_SERVER);
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("email", RandomStringUtils.random(10) + "@gmail.com", "role",
          Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER);
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "role",
          Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }
    
    @Test
    @DisplayName("Search user - Invalid params")
    void searchUserInvalidParams()
    {
      Map<String, String> queryParams = Map.of("email", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("email", RandomStringUtils.random(10) + "@gmail.com", "resourceServer",
              RandomStringUtils.randomAlphabetic(20) + "%", "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("email", RandomStringUtils.random(10) + "@gmail.com", "resourceServer",
              DUMMY_SERVER, "role", Roles.ADMIN.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams =
          Map.of("resourceServer", DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase(),
              "userId", RandomStringUtils.random(10) + "@gmail.com");
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }
    
  @Test
  @DisplayName("Search user - email does not exist")
  void searchUserEmailNoExist(KcAdminInt kc) {
      String badEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";
      Map<String, String> queryParams = Map.of("email", badEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
  }
  
  @Test
  @DisplayName("Search user - user ID does not exist")
  void searchUserIdNoExist(KcAdminInt kc) {
      String badId = UUID.randomUUID().toString();
      Map<String, String> queryParams = Map.of("userId", badId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
  }
  
  @Test
  @DisplayName("Search user - user on UAC, but not registered on COS")
  void searchUserUserOnUacOnly(KcAdminInt kc) {
      Map<String, String> queryParams = Map.of("email", noRolesEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));

      queryParams = Map.of("userId", noRolesUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
  }
  
  @Nested
  @DisplayName("Lifecycle of viewing APD when created and made active, inactive and then active")
  @TestInstance(Lifecycle.PER_CLASS)
  class SearchUserCases {
    
    String consumerUserId;
    String consumerEmail = IntegTestHelpers.email();
    
    String providerUserId;
    String providerEmail = IntegTestHelpers.email();
    
    @BeforeAll
    void setup(KcAdminInt kc)
    {
      String consumerToken = kc.createUser(consumerEmail);
      String providerToken = kc.createUser(providerEmail);

      // create consumer and provider both w/ roles for DUMMY_SERVER 
      JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));
      JsonObject provReq = new JsonObject().put("provider", new JsonArray().add(DUMMY_SERVER));

      consumerUserId = given().auth().oauth2(consumerToken).contentType(ContentType.JSON).body(consReq.toString()).when()
          .post("/user/roles").then()
          .statusCode(describedAs("Added consumer", is(200))).extract().path("results.userId");

      providerUserId = given().auth().oauth2(providerToken).contentType(ContentType.JSON).body(provReq.toString()).when()
          .post("/user/roles").then()
          .statusCode(describedAs("Added provider", is(200))).extract().path("results.userId");

      // since tokenRoles is admin of DUMMY_SERVER, use it for approval
      String providerRegId =  
          given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
          .get("/admin/provider/registrations").then()
          .statusCode(describedAs("Get provider reg. ID", is(200))).extract().path("results.find {it.userId == '%s'}.id", providerUserId);

      JsonObject approveReq = new JsonObject().put("request", new JsonArray().add(new JsonObject()
          .put("status", RoleStatus.APPROVED.toString().toLowerCase()).put("id", providerRegId)));

      given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(approveReq.toString()).when()
      .put("/admin/provider/registrations").then()
      .statusCode(describedAs("Setup - Approve provider", is(200)));
    }
    
    @Test
    @DisplayName("Searching for user w/ non existent RS")
    void rsDoesNotExist(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", consumerEmail, "resourceServer",
              RandomStringUtils.randomAlphabetic(10) + ".com", "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
      
      Map<String, String> queryParamsId = Map.of("userId", consumerUserId, "resourceServer",
              RandomStringUtils.randomAlphabetic(10) + ".com", "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
    
    @Test
    @DisplayName("Searching for user - has role, but not for the given RS")
    void rsExistNoRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", consumerEmail, "resourceServer",
              DUMMY_SERVER_NO_ONE_HAS_ROLES, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
      
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER_NO_ONE_HAS_ROLES, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
    
    @Test
    @DisplayName("User does not have consumer role")
    void userDoesNotHaveConsumerRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", providerEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
      
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
    
    @Test
    @DisplayName("User does not have provider role")
    void userDoesNotHaveProviderRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", consumerEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
      
      Map<String, String> queryParamsId = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(404)
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()))
          .body("title", equalTo(ERR_TITLE_USER_NOT_FOUND))
          .body("detail", equalTo(ERR_DETAIL_USER_NOT_FOUND));
    }
    
    @Test
    @DisplayName("Successfully find consumer")
    void searchFindConsumer(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", consumerEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_FOUND))
          .body("results.email", equalTo(consumerEmail))
          .body("results.userId", equalTo(consumerUserId))
          .body("results.name", hasKey("firstName"))
          .body("results.name", hasKey("lastName"));
      
      Map<String, String> queryParamsId = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_FOUND))
          .body("results.email", equalTo(consumerEmail))
          .body("results.userId", equalTo(consumerUserId))
          .body("results.name", hasKey("firstName"))
          .body("results.name", hasKey("lastName"));
    }
    
    @Test
    @DisplayName("Successfully find provider")
    void searchFindProvider(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("email", providerEmail, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/user/search").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_FOUND))
          .body("results.email", equalTo(providerEmail))
          .body("results.userId", equalTo(providerUserId))
          .body("results.name", hasKey("firstName"))
          .body("results.name", hasKey("lastName"));
      
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/user/search").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_USER_FOUND))
          .body("results.email", equalTo(providerEmail))
          .body("results.userId", equalTo(providerUserId))
          .body("results.name", hasKey("firstName"))
          .body("results.name", hasKey("lastName"));
    }
  }
  
  
  @Test
  @DisplayName("Consumer, provider, admin, delegate cannot call get delegate emails")
  void rolesCannotCallDelegEmailApis()
  {
      given().when().headers(rolesClientCreds)
          .queryParams(Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase()))
          .get("/delegations/emails").then().statusCode(401).and()
          .body("type", equalTo(Urn.URN_INVALID_ROLE.toString())).and()
          .body("title", equalTo(ERR_TITLE_NOT_TRUSTEE))
          .body("detail", equalTo(ERR_DETAIL_NOT_TRUSTEE));
    }
    
    @Test
    @DisplayName("Search user - Missing and invalid params")
    void getDelegateEmailsMissingAndInvalidParams()
    {
      Map<String, String> queryParams = Map.of("resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER);
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "role",
          Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              RandomStringUtils.randomAlphabetic(20) + "%", "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams = Map.of("userId", UUID.randomUUID().toString(), "resourceServer",
              DUMMY_SERVER, "role", Roles.ADMIN.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
      
      queryParams =
          Map.of("resourceServer", DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase(),
              "userId", RandomStringUtils.random(10) + "@gmail.com");
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(400).and()
          .body("type", equalTo(Urn.URN_INVALID_INPUT.toString()));
    }
    
  @Test
  @DisplayName("Get delegate emails - user ID does not exist - get empty response")
  void getDelegateEmailsUserIdNoExist(KcAdminInt kc) {
      String badId = UUID.randomUUID().toString();
      
      Map<String, String> queryParams = Map.of("userId", badId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
  }
  
  @Test
  @DisplayName("Get delegate emails - user on UAC, but not registered on COS - get empty response")
  void getDelegateEmailsUserOnUacOnly(KcAdminInt kc) {
      Map<String, String> queryParams = Map.of("userId", noRolesUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParams)
          .get("/delegations/emails").then().statusCode(200).and()
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
  }
  
  @Nested
  @DisplayName("Scenarios for get delegate emails")
  @TestMethodOrder(OrderAnnotation.class)
  @TestInstance(Lifecycle.PER_CLASS)
  class GetDelegateEmailsCases {
    
    String consumerUserId;
    String providerUserId;
    
    String consumerToken;
    String providerToken;
    
    String delegateAlphaEmail = IntegTestHelpers.email();
    String delegateBravoEmail = IntegTestHelpers.email();
    
    @BeforeAll
    void setup(KcAdminInt kc)
    {
      consumerToken = kc.createUser(IntegTestHelpers.email());
      providerToken = kc.createUser(IntegTestHelpers.email());
      
      kc.createUser(delegateAlphaEmail);
      kc.createUser(delegateBravoEmail);
      
      // create consumer and provider both w/ roles for DUMMY_SERVER
      JsonObject consReq = new JsonObject().put("consumer", new JsonArray().add(DUMMY_SERVER));
      JsonObject provReq = new JsonObject().put("provider", new JsonArray().add(DUMMY_SERVER));

      consumerUserId = given().auth().oauth2(consumerToken).contentType(ContentType.JSON).body(consReq.toString()).when()
          .post("/user/roles").then()
          .statusCode(describedAs("Added consumer", is(200))).extract().path("results.userId");

      providerUserId = given().auth().oauth2(providerToken).contentType(ContentType.JSON).body(provReq.toString()).when()
          .post("/user/roles").then()
          .statusCode(describedAs("Added provider", is(200))).extract().path("results.userId");

      // since tokenRoles is admin of DUMMY_SERVER, use it for approval
      String providerRegId =  
          given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).when()
          .get("/admin/provider/registrations").then()
              .statusCode(describedAs("Get provider reg. ID", is(200))).extract()
              .path("results.find {it.userId == '%s'}.id", providerUserId);

      JsonObject approveReq = new JsonObject().put("request", new JsonArray().add(new JsonObject()
          .put("status", RoleStatus.APPROVED.toString().toLowerCase()).put("id", providerRegId)));

      given().auth().oauth2(tokenRoles).contentType(ContentType.JSON).body(approveReq.toString()).when()
      .put("/admin/provider/registrations").then()
      .statusCode(describedAs("Setup - Approve provider", is(200)));  
    }
    
    @Test
    @Order(1)
    @DisplayName("Getting delegs emails users have no delegates - empty response")
    void noDelegates(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
      
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
    }
    
    @Test
    @Order(2)
    @DisplayName("Setup - Creating delegations")
    void createDelegs(KcAdminInt kc)
    {
      // make delegateAlpha consumer-delegate and delegateBeta for DUMMY_SERVER
      JsonObject consumerDelegateReq = new JsonObject().put("request", new JsonArray()
          .add(new JsonObject().put("userEmail", delegateAlphaEmail).put("resSerUrl", DUMMY_SERVER)
              .put("role", Roles.CONSUMER.toString().toLowerCase()))
          .add(
              new JsonObject().put("userEmail", delegateBravoEmail).put("resSerUrl", DUMMY_SERVER)
                  .put("role", Roles.CONSUMER.toString().toLowerCase())));

      given().auth().oauth2(consumerToken).contentType(ContentType.JSON).body(consumerDelegateReq.toString()).when()
      .post("/delegations").then()
      .statusCode(describedAs("Create consumer-delegates", is(201)));

      // make delegateAlpha provider-delegate for DUMMY_SERVER
      JsonObject providerDelegateReq = new JsonObject().put("request", new JsonArray()
          .add(new JsonObject().put("userEmail", delegateAlphaEmail).put("resSerUrl", DUMMY_SERVER)
              .put("role", Roles.PROVIDER.toString().toLowerCase())));

      given().auth().oauth2(providerToken).contentType(ContentType.JSON).body(providerDelegateReq.toString()).when()
      .post("/delegations").then()
      .statusCode(describedAs("Create provider-delegates", is(201)));
    }
    
    // Only ordering the first 2 tests to test for no delegates case
    
    @Test
    @DisplayName("Getting deleg emails w/ non existent RS - empty response")
    void rsDoesNotExist(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("userId", consumerUserId, "resourceServer",
              RandomStringUtils.randomAlphabetic(10) + ".com", "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
    }
    
    @Test
    @DisplayName("Getting delegs emails - user has role but not for the given RS - empty response")
    void rsExistNoRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsEmail = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER_NO_ONE_HAS_ROLES, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsEmail)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
      
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER_NO_ONE_HAS_ROLES, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
    }
    
    @Test
    @DisplayName("Getting deleg emails - User does not have consumer role - empty response")
    void userDoesNotHaveConsumerRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
    }
    
    @Test
    @DisplayName("Getting deleg emails - User does not have provider role - empty response")
    void userDoesNotHaveProviderRole(KcAdminInt kc)
    {
      Map<String, String> queryParamsId = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(0));
    }
    
    @Test
    @DisplayName("Getting deleg emails - get 2 consumer delegates for DUMMY_SERVER - non-empty response")
    void getConsumerDelegateEmails(KcAdminInt kc)
    {
      Map<String, String> queryParamsId = Map.of("userId", consumerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.CONSUMER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(2))
          .body("results.delegateEmails", hasItems(delegateAlphaEmail, delegateBravoEmail));
    }
    
    @Test
    @DisplayName("Getting deleg emails - get 1 provider delegate for DUMMY_SERVER - non-empty response")
    void getProviderDelegateEmails(KcAdminInt kc)
    {
      Map<String, String> queryParamsId = Map.of("userId", providerUserId, "resourceServer",
              DUMMY_SERVER, "role", Roles.PROVIDER.toString().toLowerCase());
      
      given().when().headers(trusteeClientCreds)
      .queryParams(queryParamsId)
          .get("/delegations/emails").then().statusCode(200)
          .body("type", equalTo(Urn.URN_SUCCESS.toString()))
          .body("title", equalTo(SUCC_TITLE_DELEG_EMAILS))
          .body("results.delegateEmails", hasSize(1))
          .body("results.delegateEmails", hasItems(delegateAlphaEmail));
    }
  }
  
}
