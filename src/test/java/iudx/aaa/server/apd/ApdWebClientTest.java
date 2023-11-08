package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.APD_REQ_CONTEXT;
import static iudx.aaa.server.apd.Constants.APD_REQ_ITEM;
import static iudx.aaa.server.apd.Constants.APD_REQ_OWNER;
import static iudx.aaa.server.apd.Constants.APD_REQ_USER;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/** Unit tests for {@link ApdWebClient} for /verify call. */
@ExtendWith({VertxExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class ApdWebClientTest {
  private static ApdWebClient apdWebClient;
  private static TestApdServerVerticle verticle = new TestApdServerVerticle();

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {

    /*
     * Deploying with timeout 4000 instead of picking up from config file. Deploying with no SSL
     * checks. Redirects are always not allowed.
     */
    JsonObject apdWebCliConfig = new JsonObject().put(Constants.CONFIG_WEBCLI_TIMEOUTMS, 4000);

    WebClientOptions webClientOptions =
        new WebClientOptions()
            .setSsl(false)
            .setVerifyHost(false)
            .setTrustAll(false)
            .setFollowRedirects(false);
    WebClient webClient = WebClient.create(vertx, webClientOptions);

    /* TestApdServiceVerticle starts on port 7331, so using this constructor */
    apdWebClient = new ApdWebClient(webClient, apdWebCliConfig, 7331);

    vertx.deployVerticle(
        verticle,
        handler -> {
          if (handler.succeeded()) {
            testContext.completeNow();
          } else {
            handler.cause().printStackTrace();
          }
        });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    verticle.stop();
    testContext.completeNow();
  }

  @Order(1)
  @RepeatedTest(TestApdServerVerticle.VERIFY_ERRORS)
  @DisplayName("Test post verify error cases")
  void testPostVerifyErrors(VertxTestContext testContext) {
    /*
     * We just put empty objects for user, owner and item and dummy placeholders for the auth token
     */
    JsonObject request =
        new JsonObject()
            .put(APD_REQ_USER, new JsonObject())
            .put(APD_REQ_OWNER, new JsonObject())
            .put(APD_REQ_ITEM, new JsonObject())
            .put(APD_REQ_CONTEXT, new JsonObject().put("TestError", true));
    testContext
        .assertFailure(apdWebClient.callVerifyApdEndpoint("localhost", "token", request))
        .recover(
            r -> {
              assertTrue(r instanceof ComposeException);
              assertTrue(r.getLocalizedMessage().equals(ERR_DETAIL_APD_NOT_RESPOND));
              return Future.succeededFuture();
            })
        .onSuccess(x -> testContext.completeNow());
  }

  @Order(2)
  @Test
  @DisplayName("Test post verify allow")
  void testPostVerifyAllow(VertxTestContext testContext) {
    JsonObject request =
        new JsonObject()
            .put(APD_REQ_USER, new JsonObject())
            .put(APD_REQ_OWNER, new JsonObject())
            .put(APD_REQ_ITEM, new JsonObject())
            .put(APD_REQ_CONTEXT, new JsonObject().put("TestAllow", true));
    testContext
        .assertComplete(apdWebClient.callVerifyApdEndpoint("localhost", "token", request))
        .compose(
            r -> {
              assertEquals(r.getString(APD_RESP_TYPE), APD_URN_ALLOW);
              return Future.succeededFuture();
            })
        .onSuccess(x -> testContext.completeNow());
  }

  // add Test post verify allow with constraints
  @Order(3)
  @Test
  @DisplayName("Test post verify allow with constraints")
  void testPostVerifyAllowWCons(VertxTestContext testContext) {
    JsonObject request =
        new JsonObject()
            .put(APD_REQ_USER, new JsonObject())
            .put(APD_REQ_OWNER, new JsonObject())
            .put(APD_REQ_ITEM, new JsonObject())
            .put(APD_REQ_CONTEXT, new JsonObject().put("TestSuccessWConstraints", true));
    testContext
        .assertComplete(apdWebClient.callVerifyApdEndpoint("localhost", "token", request))
        .compose(
            r -> {
              assertEquals(r.getString(APD_RESP_TYPE), APD_URN_ALLOW);
              assertTrue(r.containsKey(APD_CONSTRAINTS));
              return Future.succeededFuture();
            })
        .onSuccess(x -> testContext.completeNow());
  }

  @Order(4)
  @Test
  @DisplayName("Test post verify deny")
  void testPostVerifyDeny(VertxTestContext testContext) {
    JsonObject request =
        new JsonObject()
            .put(APD_REQ_USER, new JsonObject())
            .put(APD_REQ_OWNER, new JsonObject())
            .put(APD_REQ_ITEM, new JsonObject())
            .put(APD_REQ_CONTEXT, new JsonObject().put("TestDeny", true));
    testContext
        .assertComplete(apdWebClient.callVerifyApdEndpoint("localhost", "token", request))
        .compose(
            r -> {
              assertEquals(r.getString(APD_RESP_TYPE), APD_URN_DENY);
              assertTrue(r.containsKey(APD_RESP_DETAIL));
              assertTrue(r.getString(APD_RESP_DETAIL) != null);
              return Future.succeededFuture();
            })
        .onSuccess(x -> testContext.completeNow());
  }

  @Order(5)
  @Test
  @DisplayName("Test post verify deny-needs-interaction")
  void testPostVerifyDenyNeedsInteraction(VertxTestContext testContext) {
    JsonObject request =
        new JsonObject()
            .put(APD_REQ_USER, new JsonObject())
            .put(APD_REQ_OWNER, new JsonObject())
            .put(APD_REQ_ITEM, new JsonObject())
            .put(APD_REQ_CONTEXT, new JsonObject().put("TestDenyNInteraction", true));
    testContext
        .assertComplete(apdWebClient.callVerifyApdEndpoint("localhost", "token", request))
        .compose(
            r -> {
              assertEquals(r.getString(APD_RESP_TYPE), APD_URN_DENY_NEEDS_INT);
              assertTrue(r.containsKey(APD_RESP_DETAIL));
              assertTrue(r.containsKey(APD_RESP_SESSIONID));
              assertTrue(r.getString(APD_RESP_DETAIL) != null);
              assertTrue(r.getString(APD_RESP_SESSIONID) != null);
              return Future.succeededFuture();
            })
        .onSuccess(x -> testContext.completeNow());
  }
}
