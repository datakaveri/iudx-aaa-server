package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.CALL_APD_APDURL;
import static iudx.aaa.server.policy.Constants.CALL_APD_CONTEXT;
import static iudx.aaa.server.policy.Constants.CALL_APD_ITEM_ID;
import static iudx.aaa.server.policy.Constants.CALL_APD_ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.CALL_APD_OWNERID;
import static iudx.aaa.server.policy.Constants.CALL_APD_RES_SER_URL;
import static iudx.aaa.server.policy.Constants.CALL_APD_USERID;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_DID;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_DRL;
import static iudx.aaa.server.policy.Constants.CREATE_TOKEN_RG;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_ID;
import static iudx.aaa.server.policy.Constants.INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.NOT_RES_OWNER;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCCESS;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.token.Constants.ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for verify resource access. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class VerifyResourceAccessTest {
  private static final Logger LOGGER = LogManager.getLogger(VerifyResourceAccessTest.class);

  private static Configuration config;

  /* Database Properties */
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pgclient;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  private static Vertx vertxObj;

  private static final String DUMMY_COS_URL =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
  private static final String DUMMY_SERVER =
      RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(0, vertx);

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions =
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword)
              .setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */

    PgPool pool = PgPool.pool(vertx, connectOptions, poolOptions);
    policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient);
    testContext.completeNow();
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("COS Admin, admin cannot get resource token")
  void cosAdminAndAdminNoGetResToken(VertxTestContext testContext) {
    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.COS_ADMIN, Roles.ADMIN))
            .rolesToRsMapping(
                Map.of(
                    Roles.COS_ADMIN.toString(),
                    new JsonArray().add(DUMMY_COS_URL),
                    Roles.ADMIN.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    Checkpoint adminFail = testContext.checkpoint();
    Checkpoint cosAdminFail = testContext.checkpoint();

    JsonObject cosAdminJsonReq =
        new JsonObject()
            .put("itemId", UUID.randomUUID().toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.COS_ADMIN.toString().toLowerCase());
    RequestToken cosAdminReq = new RequestToken(cosAdminJsonReq);

    policyService
        .verifyResourceAccess(cosAdminReq, null, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), INVALID_ROLE);
                          cosAdminFail.flag();
                        })));

    JsonObject adminJsonReq =
        new JsonObject()
            .put("itemId", UUID.randomUUID().toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.ADMIN.toString().toLowerCase());
    RequestToken adminReq = new RequestToken(adminJsonReq);

    policyService
        .verifyResourceAccess(adminReq, null, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), INVALID_ROLE);
                          adminFail.flag();
                        })));
  }

  @Test
  @DisplayName("Invalid item ID - not a UUID")
  void itemNotUuid(VertxTestContext testContext) {
    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER, Roles.DELEGATE, Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.DELEGATE.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.CONSUMER.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    List<Roles> rolesExceptDelegate = List.of(Roles.PROVIDER, Roles.CONSUMER);
    Map<Roles, Checkpoint> checkpoints =
        rolesExceptDelegate.stream()
            .collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));

    Checkpoint consDelegCheck = testContext.checkpoint();
    Checkpoint provDelegCheck = testContext.checkpoint();

    rolesExceptDelegate.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", RandomStringUtils.randomAlphanumeric(10))
                  .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
                  .put("role", role.toString().toLowerCase());
          RequestToken req = new RequestToken(jsonReq);

          policyService
              .verifyResourceAccess(req, null, dummyUser)
              .onComplete(
                  testContext.failing(
                      fail ->
                          testContext.verify(
                              () -> {
                                assertTrue(fail instanceof ComposeException);
                                ComposeException exp = (ComposeException) fail;
                                Response resp = exp.getResponse();
                                assertEquals(resp.getStatus(), 400);
                                assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                                assertEquals(resp.getTitle(), INVALID_INPUT);
                                assertEquals(resp.getDetail(), INCORRECT_ITEM_ID);
                                checkpoints.get(role).flag();
                              })));
        });

    JsonObject delegateJsonReq =
        new JsonObject()
            .put("itemId", RandomStringUtils.randomAlphanumeric(10))
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());
    RequestToken delegateReq = new RequestToken(delegateJsonReq);

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.CONSUMER, DUMMY_SERVER);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.PROVIDER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(delegateReq, consDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 400);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), INVALID_INPUT);
                          assertEquals(resp.getDetail(), INCORRECT_ITEM_ID);
                          consDelegCheck.flag();
                        })));

    policyService
        .verifyResourceAccess(delegateReq, provDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 400);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), INVALID_INPUT);
                          assertEquals(resp.getDetail(), INCORRECT_ITEM_ID);
                          provDelegCheck.flag();
                        })));
  }

  @Test
  @DisplayName("Invalid item ID - Catalogue client validation fails w/ ComposeException")
  void catClientValidationForItemFails(VertxTestContext testContext) {
    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER, Roles.DELEGATE, Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.DELEGATE.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.CONSUMER.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID badResId = UUID.randomUUID();

    Mockito.when(catalogueClient.getResourceDetails(badResId))
        .thenReturn(
            Future.failedFuture(new ComposeException(400, Urn.URN_INVALID_INPUT, "Fail", "Fail")));

    List<Roles> rolesExceptDelegate = List.of(Roles.PROVIDER, Roles.CONSUMER);
    Map<Roles, Checkpoint> checkpoints =
        rolesExceptDelegate.stream()
            .collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));

    Checkpoint consDelegCheck = testContext.checkpoint();
    Checkpoint provDelegCheck = testContext.checkpoint();

    rolesExceptDelegate.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", badResId.toString())
                  .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
                  .put("role", role.toString().toLowerCase());
          RequestToken req = new RequestToken(jsonReq);

          policyService
              .verifyResourceAccess(req, null, dummyUser)
              .onComplete(
                  testContext.failing(
                      fail ->
                          testContext.verify(
                              () -> {
                                assertTrue(fail instanceof ComposeException);
                                ComposeException exp = (ComposeException) fail;
                                Response resp = exp.getResponse();
                                assertEquals(resp.getStatus(), 400);
                                assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                                assertEquals(resp.getTitle(), "Fail");
                                assertEquals(resp.getDetail(), "Fail");
                                checkpoints.get(role).flag();
                              })));
        });

    JsonObject delegateJsonReq =
        new JsonObject()
            .put("itemId", badResId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());

    RequestToken delegateReq = new RequestToken(delegateJsonReq);

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.CONSUMER, DUMMY_SERVER);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.PROVIDER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(delegateReq, consDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 400);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), "Fail");
                          assertEquals(resp.getDetail(), "Fail");
                          consDelegCheck.flag();
                        })));

    policyService
        .verifyResourceAccess(delegateReq, provDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 400);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), "Fail");
                          assertEquals(resp.getDetail(), "Fail");
                          provDelegCheck.flag();
                        })));
  }

  @Test
  @DisplayName("Consumer/Provider do not have role for resource server of requested resource item")
  void consumerProviderDontHaveRoleForItemRs(VertxTestContext testContext) {
    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER, Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.CONSUMER.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID resId = UUID.randomUUID();
    String RANDOM_SERVER = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    UUID.randomUUID(),
                    RANDOM_SERVER,
                    UUID.randomUUID(),
                    RandomStringUtils.randomAlphabetic(10).toLowerCase(),
                    "SECURE")));

    Checkpoint consumerFails = testContext.checkpoint();
    Checkpoint providerFails = testContext.checkpoint();

    JsonObject consJsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase());
    RequestToken consReq = new RequestToken(consJsonReq);

    policyService
        .verifyResourceAccess(consReq, null, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE);
                          consumerFails.flag();
                        })));

    JsonObject provJsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.PROVIDER.toString().toLowerCase());
    RequestToken provReq = new RequestToken(provJsonReq);

    policyService
        .verifyResourceAccess(provReq, null, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE);
                          providerFails.flag();
                        })));
  }

  @Test
  @DisplayName(
      "Delegate - Delegated RS URL does not match the resource server of requested resource item")
  void delegatedRsUrlNotMatchItemRs(VertxTestContext testContext) {

    String SERVER_OF_RES = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    UUID resId = UUID.randomUUID();

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    UUID.randomUUID(),
                    SERVER_OF_RES,
                    UUID.randomUUID(),
                    RandomStringUtils.randomAlphabetic(10).toLowerCase(),
                    "SECURE")));

    // note that the delegate has role for both SERVER_OF_RES and DUMMY_SERVER, but the delegation
    // is for DUMMY_SERVER
    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(
                    Roles.DELEGATE.toString(), new JsonArray(List.of(SERVER_OF_RES, DUMMY_SERVER))))
            .build();

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.CONSUMER, DUMMY_SERVER);
    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), UUID.randomUUID(), Roles.PROVIDER, DUMMY_SERVER);

    Checkpoint consDelegateFail = testContext.checkpoint();
    Checkpoint provDelegateFail = testContext.checkpoint();

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    policyService
        .verifyResourceAccess(req, consDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(
                              resp.getDetail(), ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS);
                          consDelegateFail.flag();
                        })));

    policyService
        .verifyResourceAccess(req, provDelegInfo, dummyUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(
                              resp.getDetail(), ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS);
                          provDelegateFail.flag();
                        })));
  }

  @Test
  @DisplayName("Provider does not own item")
  void providerDoesntOwnItem(VertxTestContext testContext) {

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.randomUUID();
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(Roles.PROVIDER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.PROVIDER.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    policyService
        .verifyResourceAccess(req, null, providerUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), NOT_RES_OWNER);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider owns item, but item is PII resource")
  void providerCannotAccessPiiResource(VertxTestContext testContext) {

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(Roles.PROVIDER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.fromString(providerUser.getUserId());
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "PII")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.PROVIDER.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    policyService
        .verifyResourceAccess(req, null, providerUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider-delegate's delegator does not own item")
  void providerDelegateDoesntOwnItem(VertxTestContext testContext) {

    // User ID of the provider user who made the delegation a.k.a delegator
    UUID providerDelegatorUserId = UUID.randomUUID();

    User delegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(Roles.DELEGATE.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.randomUUID();
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), providerDelegatorUserId, Roles.PROVIDER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(req, provDelegInfo, delegateUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), NOT_RES_OWNER);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider-delegate's delegator owns item, but item is PII resource")
  void providerDelegateCannotAccessPiiResource(VertxTestContext testContext) {

    // User ID of the provider user who made the delegation a.k.a delegator
    UUID providerDelegatorUserId = UUID.randomUUID();

    User delegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(Roles.DELEGATE.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = providerDelegatorUserId;
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "PII")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), providerDelegatorUserId, Roles.PROVIDER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(req, provDelegInfo, delegateUser)
        .onComplete(
            testContext.failing(
                fail ->
                    testContext.verify(
                        () -> {
                          assertTrue(fail instanceof ComposeException);
                          ComposeException exp = (ComposeException) fail;
                          Response resp = exp.getResponse();
                          assertEquals(resp.getStatus(), 403);
                          assertEquals(resp.getType(), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(resp.getTitle(), ACCESS_DENIED);
                          assertEquals(resp.getDetail(), ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider success")
  void providerSuccess(VertxTestContext testContext) {

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(Roles.PROVIDER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.fromString(providerUser.getUserId());
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.PROVIDER.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    policyService
        .verifyResourceAccess(req, null, providerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getString(STATUS), SUCCESS);
                          assertEquals(response.getString(CAT_ID), resId.toString());
                          assertEquals(response.getString(CREATE_TOKEN_RG), resGroupId.toString());
                          assertEquals(response.getString(URL), DUMMY_SERVER);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider-delegate success")
  void providerDelegateSuccess(VertxTestContext testContext) {

    // User ID of the provider user who made the delegation a.k.a delegator
    UUID providerDelegatorUserId = UUID.randomUUID();

    User delegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(Roles.DELEGATE.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = providerDelegatorUserId;
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase());

    RequestToken req = new RequestToken(jsonReq);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), providerDelegatorUserId, Roles.PROVIDER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(req, provDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getString(STATUS), SUCCESS);
                          assertEquals(response.getString(CAT_ID), resId.toString());
                          assertEquals(response.getString(CREATE_TOKEN_RG), resGroupId.toString());
                          assertEquals(response.getString(URL), DUMMY_SERVER);

                          assertEquals(
                              response.getString(CREATE_TOKEN_DID),
                              providerDelegatorUserId.toString());
                          assertEquals(
                              response.getString(CREATE_TOKEN_DRL), Roles.PROVIDER.toString());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Consumer success")
  void consumerSuccess(VertxTestContext testContext) {

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.randomUUID();
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject tokenReqcontext = new JsonObject().put("access", 1);

    JsonObject apdContext =
        new JsonObject()
            .put(CALL_APD_APDURL, apdUrl)
            .put(CALL_APD_ITEM_ID, resId.toString())
            .put(CALL_APD_ITEM_TYPE, ItemType.RESOURCE.toString().toLowerCase())
            .put(CALL_APD_OWNERID, itemOwnerUserId.toString())
            .put(CALL_APD_RES_SER_URL, DUMMY_SERVER)
            .put(CALL_APD_USERID, consumerUser.getUserId())
            .put(CALL_APD_CONTEXT, tokenReqcontext);

    /*
     * Since verifyPolicy does not check the response of callApd, we just send an empty JSON object
     * as the mocked response for callApd here.
     */
    Mockito.when(apdService.callApd(apdContext))
        .thenReturn(Future.succeededFuture(new JsonObject()));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.CONSUMER.toString().toLowerCase())
            .put("context", tokenReqcontext);

    RequestToken req = new RequestToken(jsonReq);

    policyService
        .verifyResourceAccess(req, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getString(CREATE_TOKEN_RG), resGroupId.toString());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Consumer-delegate success")
  void consumerDelegateSuccess(VertxTestContext testContext) {

    // User ID of the consumer user who made the delegation a.k.a delegator
    UUID consumerDelegatorUserId = UUID.randomUUID();

    UUID resId = UUID.randomUUID();
    UUID resGroupId = UUID.randomUUID();
    UUID itemOwnerUserId = UUID.randomUUID();
    String apdUrl = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".apd.com";

    User delegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(Roles.DELEGATE.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .build();

    Mockito.when(catalogueClient.getResourceDetails(resId))
        .thenReturn(
            Future.succeededFuture(
                new ResourceObj(
                    ItemType.RESOURCE,
                    resId,
                    itemOwnerUserId,
                    DUMMY_SERVER,
                    resGroupId,
                    apdUrl,
                    "SECURE")));

    JsonObject tokenReqcontext = new JsonObject().put("access", 1);

    JsonObject apdContext =
        new JsonObject()
            .put(CALL_APD_APDURL, apdUrl)
            .put(CALL_APD_ITEM_ID, resId.toString())
            .put(CALL_APD_ITEM_TYPE, ItemType.RESOURCE.toString().toLowerCase())
            .put(CALL_APD_OWNERID, itemOwnerUserId.toString())
            .put(CALL_APD_RES_SER_URL, DUMMY_SERVER)
            .put(CALL_APD_USERID, consumerDelegatorUserId.toString())
            .put(CALL_APD_CONTEXT, tokenReqcontext);

    /*
     * Since verifyResourceAccess does not check the response of callApd, we just send an empty JSON object
     * as the mocked response for callApd here.
     */
    Mockito.when(apdService.callApd(apdContext))
        .thenReturn(Future.succeededFuture(new JsonObject()));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", resId.toString())
            .put("itemType", ItemType.RESOURCE.toString().toLowerCase())
            .put("role", Roles.DELEGATE.toString().toLowerCase())
            .put("context", tokenReqcontext);

    RequestToken req = new RequestToken(jsonReq);

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(), consumerDelegatorUserId, Roles.CONSUMER, DUMMY_SERVER);

    policyService
        .verifyResourceAccess(req, consDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getString(CREATE_TOKEN_RG), resGroupId.toString());
                          assertEquals(
                              response.getString(CREATE_TOKEN_DID),
                              consumerDelegatorUserId.toString());
                          assertEquals(
                              response.getString(CREATE_TOKEN_DRL), Roles.CONSUMER.toString());
                          testContext.completeNow();
                        })));
  }
}
