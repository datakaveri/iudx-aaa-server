package iudx.aaa.server.admin;

import static io.restassured.RestAssured.*;
import static iudx.aaa.server.registration.Constants.*;
import static org.hamcrest.Matchers.*;
import org.apache.commons.lang3.RandomStringUtils;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.registration.IntegTestHelpers;
import iudx.aaa.server.registration.KcAdminExtension;
import iudx.aaa.server.registration.KcAdminInt;
import iudx.aaa.server.registration.RestAssuredConfigExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Miscellaneous integration tests. 
 *
 */

@ExtendWith({KcAdminExtension.class, RestAssuredConfigExtension.class})
public class MiscIT {

    @Test
    @DisplayName("Listing COS Admin")
    void listRolesCosAdmin(KcAdminInt kc) {
      given().auth().oauth2(kc.cosAdminToken).when()
      .get("/user/roles").then()
      .statusCode(200)
      .body("type", equalTo(Urn.URN_SUCCESS.toString()))
      .body("title", equalTo(SUCC_TITLE_USER_READ))
      .body("results.userId", equalTo(IntegTestHelpers.getUserIdFromKcToken(kc.cosAdminToken)))
      .body("results.roles", hasItem(Roles.COS_ADMIN.toString().toLowerCase()))
      .body("results.rolesToRsMapping.cos_admin", is(nullValue()))
      .body("results.email", not(nullValue()))
      .body("results.name.firstName", not(nullValue()))
      .body("results.name.lastName", not(nullValue()));
    }  
    
    @Test
    @DisplayName("Invalid Keycloak token")
    void invalidKcToken(KcAdminInt kc) {
      given().auth().oauth2(RandomStringUtils.random(100)).when()
      .get("/user/roles").then()
      .statusCode(401)
      .body("type", equalTo(Urn.URN_INVALID_AUTH_TOKEN.toString()));
    }  
}
