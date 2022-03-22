package iudx.aaa.server.apd;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.token.TokenService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.NIL_UUID;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_APD;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class  ListApdTest {

  private static Logger LOGGER = LogManager.getLogger(ListApdTest.class);

  private static Configuration config;

  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static Vertx vertxObj;
  private static ApdService apdService;

  private static PgPool pool;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static ApdWebClient apdWebClient = Mockito.mock(ApdWebClient.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static TokenService tokenService = Mockito.mock(TokenService.class);

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final UUID PENDING_A_ID = UUID.randomUUID();
  private static final UUID ACTIVE_A_ID = UUID.randomUUID();
  private static final UUID INACTIVE_A_ID = UUID.randomUUID();

  private static final UUID PENDING_B_ID = UUID.randomUUID();
  private static final UUID ACTIVE_B_ID = UUID.randomUUID();
  private static final UUID INACTIVE_B_ID = UUID.randomUUID();

  private static final String PENDING_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String ACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static final String PENDING_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String ACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static Future<JsonObject> trusteeUserA;
  private static Future<JsonObject> trusteeUserB;
  private static Future<JsonObject> authAdmin;
  private static Future<JsonObject> otherAdmin;

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Future<UUID> orgIdFut;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(4, vertx);

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
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Do not take test config, use generated config */
    JsonObject options = new JsonObject().put(CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(Utils.SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));
    trusteeUserA = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.TRUSTEE, RoleStatus.APPROVED), false));
    trusteeUserB = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.TRUSTEE, RoleStatus.APPROVED), false));
    authAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));
    otherAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));

    CompositeFuture.all(trusteeUserA, trusteeUserB, authAdmin, otherAdmin).compose(succ -> {
      // create servers for admins
      JsonObject admin1 = authAdmin.result();
      UUID uid1 = UUID.fromString(admin1.getString("userId"));

      JsonObject admin2 = otherAdmin.result();
      UUID uid2 = UUID.fromString(admin2.getString("userId"));
      List<Tuple> tup = List.of(Tuple.of("Auth Server", uid1, DUMMY_AUTH_SERVER),
          Tuple.of("Other Server", uid2, DUMMY_SERVER));

      /*
       * To test the different APD states, we create 3 APDs each for the 2 trustees. Slightly
       * different from other tests, we also create the UUID APD IDs and insert into the DB instead
       * of relying on the auto-create in DB
       */
      UUID trusteeIdA = UUID.fromString(trusteeUserA.result().getString("userId"));
      UUID trusteeIdB = UUID.fromString(trusteeUserB.result().getString("userId"));

      List<Tuple> apdTup = List.of(
          Tuple.of(PENDING_A_ID, PENDING_A, PENDING_A + ".com", trusteeIdA, ApdStatus.PENDING),
          Tuple.of(ACTIVE_A_ID, ACTIVE_A, ACTIVE_A + ".com", trusteeIdA, ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_A_ID, INACTIVE_A, INACTIVE_A + ".com", trusteeIdA, ApdStatus.INACTIVE),
          Tuple.of(PENDING_B_ID, PENDING_B, PENDING_B + ".com", trusteeIdB, ApdStatus.PENDING),
          Tuple.of(ACTIVE_B_ID, ACTIVE_B, ACTIVE_B + ".com", trusteeIdB, ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_B_ID, INACTIVE_B, INACTIVE_B + ".com", trusteeIdB, ApdStatus.INACTIVE));

      return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER)
          .executeBatch(tup).compose(x -> conn.preparedQuery(SQL_CREATE_APD).executeBatch(apdTup)));
    }).onSuccess(x -> {
      apdService =
          new ApdServiceImpl(pool, apdWebClient, registrationService, policyService,tokenService,options);
      testContext.completeNow();
    }).onFailure(x -> {
      apdService =  new ApdServiceImpl(pool, apdWebClient, registrationService, policyService,tokenService,options);
      testContext.completeNow();
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER).toArray());
    List<JsonObject> users = List.of(trusteeUserA.result(), otherAdmin.result(),
        trusteeUserB.result(), authAdmin.result());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(success -> Utils.deleteFakeUser(pool, users)).compose(
            succ -> conn.preparedQuery(Utils.SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }


  @DisplayName("Test incorrect apdID")
  void noApd(VertxTestContext testContext) {

    String randUuid = UUID.randomUUID().toString();
    apdService.getApdDetails(List.of(), List.of(randUuid),
            testContext.succeeding(response -> {
              testContext.verify(() -> {
                        assertEquals(response.getInteger("status"),400);
                        assertEquals(response.getString("title"),"Invalid request");
                        testContext.completeNow();
                      }
              );
            }));
  }

  @Test
  @DisplayName("Test incorrect apdUrl")
  void noUrl(VertxTestContext testContext) {

    String randString =  RandomStringUtils.randomAlphabetic(7).toLowerCase();
    apdService.getApdDetails(List.of(randString), List.of(),
            testContext.succeeding(response -> {
              testContext.verify(() -> {
                        assertEquals(response.getInteger("status"),400);
                        assertEquals(response.getString("title"),"Invalid request");
                        testContext.completeNow();
                      }
              );
            }));
  }

  @Test
  @DisplayName("Test incorrect input both apdUrl and apdID present")
  void invalidInput(VertxTestContext testContext) {

    String randUuid = UUID.randomUUID().toString();
    String randString =  RandomStringUtils.randomAlphabetic(7).toLowerCase();
    apdService.getApdDetails(List.of(randString), List.of(randUuid),
            testContext.failing(response -> {
              testContext.verify(() -> {
                        assertEquals(response.getMessage(),"internal server error");
                        testContext.completeNow();
                      }
              );
            }));
  }

  @Test
  @DisplayName("Test incorrect input both apdUrl and apdID empty")
  void emptyInput(VertxTestContext testContext) {

    apdService.getApdDetails(List.of(), List.of(),
            testContext.failing(response -> {
              testContext.verify(() -> {
                        assertEquals(response.getMessage(),"internal server error");
                        testContext.completeNow();
                      }
              );
            }));
  }

  @Test
  @DisplayName("Test multipleSuccess - apdID")
  void multipleSuccessApdId(VertxTestContext testContext) {

    List<String> request = new ArrayList<String>();
    request.add(ACTIVE_A_ID.toString());
    request.add(ACTIVE_B_ID.toString());

    JsonObject trusteeAdets = trusteeUserA.result();
    JsonObject trusteeBdets = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject trusteeA = new JsonObject().put("email", trusteeAdets.getString("email"))
              .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                      .put("lastName", trusteeAdets.getString("lastName")));

      JsonObject trusteeB = new JsonObject().put("email", trusteeBdets.getString("email"))
              .put("name", new JsonObject().put("firstName", trusteeBdets.getString("firstName"))
                      .put("lastName", trusteeBdets.getString("lastName")));

      p.complete(new JsonObject().put(trusteeAdets.getString("userId"), trusteeA)
              .put(trusteeBdets.getString("userId"), trusteeB));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    apdService.getApdDetails(
        List.of(),
        request,
        testContext.succeeding(
            response -> {
              testContext.verify(
                  () -> {
                    JsonObject respOne = response.getJsonObject(ACTIVE_A_ID.toString());
                    JsonObject respTwo = response.getJsonObject(ACTIVE_B_ID.toString());
                    assertEquals(respOne.getString("status"), "ACTIVE");
                    assertEquals(respTwo.getString("status"), "ACTIVE");
                    assertEquals(
                        respOne.getJsonObject("owner").getString("email"),
                        trusteeAdets.getString("email"));
                    assertEquals(
                        respTwo.getJsonObject("owner").getString("email"),
                        trusteeBdets.getString("email"));
                    testContext.completeNow();
                  });
            }));
  }

    @Test
    @DisplayName("Test multiple list - consumer")
    void ListInvalidUser(VertxTestContext testContext) {

        JsonObject trusteeAdets = trusteeUserA.result();
        UUID uid1 = UUID.fromString(NIL_UUID);
        Roles role = Roles.PROVIDER;
        User user = new UserBuilder().userId(uid1).roles(List.of(role)).build();

        apdService.listApd(user,
                testContext.succeeding(response -> {
                    testContext.verify(() ->
                            {
                                assertEquals(response.getString("title"),"User profile does not exist");
                                testContext.completeNow();
                            }
                    );
                }));
    }


    //authAdmin role gets all apds
    @Test
    @DisplayName("Test multiple list - authAdmin")
    void ListAuthAdmin(VertxTestContext testContext) {

        JsonObject admin1 = authAdmin.result();
        UUID uid1 = UUID.fromString(admin1.getString("userId"));
        Roles role = Roles.ADMIN;
        User user = new UserBuilder().userId(uid1).roles(List.of(role)).build();

        JsonObject trusteeAdets = trusteeUserA.result();
        JsonObject trusteeBdets = trusteeUserB.result();
        Mockito.doAnswer(i -> {
            List<String> ids = i.getArgument(0);
            Promise<JsonObject> p = i.getArgument(1);
            JsonObject response  = new JsonObject();
            ids.forEach(id -> {
                JsonObject value = new JsonObject();
                if(id.equals(trusteeAdets.getString("userId")))
                {
                     value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));

                }
                if(id.equals(trusteeBdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));
                }
                response.put(id,value);
            });


            p.complete(response);
            return i.getMock();
        }).when(registrationService).getUserDetails(any(), any());

        apdService.listApd(user,
                testContext.succeeding(response -> {
                    testContext.verify(() -> {
                                String ownerId1 = response.getJsonObject("results").
                                        getJsonObject(ACTIVE_A_ID.toString())
                                        .getJsonObject("owner").getString("id");
                                assertEquals(ownerId1,trusteeAdets.getString("userId"));
                                testContext.completeNow();
                            }
                    );
                }));
    }

    //trutee role gets all apds belonging to them and all other apds that are active
    @Test
    @DisplayName("Test multiple list - Trustee")
    void ListTrustee(VertxTestContext testContext) {

        JsonObject trusteeAdets = trusteeUserA.result();
        UUID uid1 = UUID.fromString(trusteeAdets.getString("userId"));
        Roles role = Roles.TRUSTEE;
        User user = new UserBuilder().userId(uid1).roles(List.of(role)).build();
        JsonObject trusteeBdets = trusteeUserB.result();
        Mockito.doAnswer(i -> {
            List<String> ids = i.getArgument(0);
            Promise<JsonObject> p = i.getArgument(1);
            JsonObject response  = new JsonObject();
            ids.forEach(id -> {
                JsonObject value = new JsonObject();
                if(id.equals(trusteeAdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));

                }
                if(id.equals(trusteeBdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));
                }
                response.put(id,value);
            });
            p.complete(response);
            return i.getMock();
        }).when(registrationService).getUserDetails(any(), any());

        apdService.listApd(user,
                testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        String ownerId1 = response.getJsonObject("results").
                                getJsonObject(ACTIVE_A_ID.toString())
                                .getJsonObject("owner").getString("id");

                        String status_ACTIVE_A =  response.getJsonObject("results").
                                getJsonObject(ACTIVE_A_ID.toString()).getString("status");

                        String ownerId2 = response.getJsonObject("results").
                                getJsonObject(PENDING_A_ID.toString())
                                .getJsonObject("owner").getString("id");

                        String status_PENDING_A =  response.getJsonObject("results").
                                getJsonObject(PENDING_A_ID.toString()).getString("status");

                        String ownerId3 = response.getJsonObject("results").
                                getJsonObject(INACTIVE_A_ID.toString())
                                .getJsonObject("owner").getString("id");

                        String status_INACTIVE_A =  response.getJsonObject("results").
                                getJsonObject(INACTIVE_A_ID.toString()).getString("status");

                        String ownerId4 = response.getJsonObject("results").
                                getJsonObject(ACTIVE_B_ID.toString())
                                .getJsonObject("owner").getString("id");

                        String status_ACTIVE_B =  response.getJsonObject("results").
                                getJsonObject(ACTIVE_B_ID.toString()).getString("status");

                        assertEquals(ownerId1,trusteeAdets.getString("userId"));
                        assertEquals(status_ACTIVE_A, ApdStatus.ACTIVE.toString());
                        assertEquals(ownerId2,trusteeAdets.getString("userId"));
                        assertEquals(status_PENDING_A, ApdStatus.PENDING.toString());
                        assertEquals(ownerId3,trusteeAdets.getString("userId"));
                        assertEquals(status_INACTIVE_A, ApdStatus.INACTIVE.toString());
                        assertEquals(ownerId4,trusteeBdets.getString("userId"));
                        assertEquals(status_ACTIVE_B, ApdStatus.ACTIVE.toString());
                                testContext.completeNow();
                            }
                    );
                }));
    }


    //provider role gets all active apds
    @Test
    @DisplayName("Test multiple list - provider")
    void ListApdProvider(VertxTestContext testContext) {

        JsonObject trusteeAdets = trusteeUserA.result();
        UUID uid1 = UUID.fromString(trusteeAdets.getString("userId"));
        Roles role = Roles.PROVIDER;
        User user = new UserBuilder().userId(uid1).roles(List.of(role)).build();
        JsonObject trusteeBdets = trusteeUserB.result();
        Mockito.doAnswer(i -> {
            List<String> ids = i.getArgument(0);
            Promise<JsonObject> p = i.getArgument(1);
            JsonObject response  = new JsonObject();
            ids.forEach(id -> {
                JsonObject value = new JsonObject();
                if(id.equals(trusteeAdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));

                }
                if(id.equals(trusteeBdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));
                }
                response.put(id,value);
            });
            p.complete(response);
            return i.getMock();
        }).when(registrationService).getUserDetails(any(), any());

        apdService.listApd(user,
                testContext.succeeding(response -> {
                    testContext.verify(() -> {
                                String ownerId1 = response.getJsonObject("results").
                                        getJsonObject(ACTIVE_A_ID.toString())
                                        .getJsonObject("owner").getString("id");

                                String ownerId2 = response.getJsonObject("results").
                                        getJsonObject(ACTIVE_B_ID.toString())
                                        .getJsonObject("owner").getString("id");

                                assertEquals(ownerId1,trusteeAdets.getString("userId"));
                                assertEquals(ownerId2,trusteeBdets.getString("userId"));
                                testContext.completeNow();
                            }
                    );
                }));
    }

    //consumer role gets all active apds
    @Test
    @DisplayName("Test multiple list - consumer")
    void ListApdConsumer(VertxTestContext testContext) {

        JsonObject trusteeAdets = trusteeUserA.result();
        UUID uid1 = UUID.fromString(trusteeAdets.getString("userId"));
        Roles role = Roles.PROVIDER;
        User user = new UserBuilder().userId(uid1).roles(List.of(role)).build();
        JsonObject trusteeBdets = trusteeUserB.result();
        Mockito.doAnswer(i -> {
            List<String> ids = i.getArgument(0);
            Promise<JsonObject> p = i.getArgument(1);
            JsonObject response  = new JsonObject();
            ids.forEach(id -> {
                JsonObject value = new JsonObject();
                if(id.equals(trusteeAdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));

                }
                if(id.equals(trusteeBdets.getString("userId")))
                {
                    value = new JsonObject().put("email", trusteeAdets.getString("email"))
                            .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
                                    .put("lastName", trusteeAdets.getString("lastName")));
                }
                response.put(id,value);
            });
            p.complete(response);
            return i.getMock();
        }).when(registrationService).getUserDetails(any(), any());

        apdService.listApd(user,
                testContext.succeeding(response -> {
                    testContext.verify(() -> {
                                String ownerId1 = response.getJsonObject("results").
                                        getJsonObject(ACTIVE_A_ID.toString())
                                        .getJsonObject("owner").getString("id");

                                String ownerId2 = response.getJsonObject("results").
                                        getJsonObject(ACTIVE_B_ID.toString())
                                        .getJsonObject("owner").getString("id");

                                assertEquals(ownerId1,trusteeAdets.getString("userId"));
                                assertEquals(ownerId2,trusteeBdets.getString("userId"));
                                testContext.completeNow();
                            }
                    );
                }));
    }
}
