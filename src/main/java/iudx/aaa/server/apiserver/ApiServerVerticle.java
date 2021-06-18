package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.Constants.HEADER_ACCEPT;
import static iudx.aaa.server.apiserver.Constants.HEADER_ALLOW_ORIGIN;
import static iudx.aaa.server.apiserver.Constants.HEADER_CONTENT_LENGTH;
import static iudx.aaa.server.apiserver.Constants.HEADER_CONTENT_TYPE;
import static iudx.aaa.server.apiserver.Constants.HEADER_HOST;
import static iudx.aaa.server.apiserver.Constants.HEADER_ORIGIN;
import static iudx.aaa.server.apiserver.Constants.HEADER_REFERER;
import static iudx.aaa.server.apiserver.Constants.HEADER_TOKEN;
import static iudx.aaa.server.apiserver.Constants.MIME_APPLICATION_JSON;
import static iudx.aaa.server.apiserver.Constants.MIME_TEXT_HTML;
import static iudx.aaa.server.apiserver.Constants.ROUTE_DOC;
import static iudx.aaa.server.apiserver.Constants.ROUTE_STATIC_SPEC;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.tip.TIPService;
import iudx.aaa.server.token.TokenService;
import iudx.aaa.server.twofactor.TwoFactorService;

/**
 * The AAA Server API Verticle.
 * <h1>AAA Server API Verticle</h1>
 * <p>
 * The API Server verticle implements the IUDX AAA Server APIs. It handles the API requests from the
 * clients and interacts with the associated Service to respond.
 * </p>
 * 
 * @see io.vertx.core.Vertx
 * @see io.vertx.core.AbstractVerticle
 * @see io.vertx.core.http.HttpServer
 * @see io.vertx.ext.web.Router
 * @see io.vertx.servicediscovery.ServiceDiscovery
 * @see io.vertx.servicediscovery.types.EventBusService
 * @see io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
 * @version 1.0
 * @since 2020-12-15
 */

public class ApiServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(ApiServerVerticle.class);
  private HttpServer server;
  private Router router;
  private final int port = 8443;
  private boolean isSSL;
  private String keystore;
  private String keystorePassword;

  /** Service addresses */
  private static final String TIP_SERVICE_ADDRESS = "iudx.aaa.tip.service";
  private static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private static final String TWOFACTOR_SERVICE_ADDRESS = "iudx.aaa.twofactor.service";

  private TIPService tipService;
  private PolicyService policyService;
  private RegistrationService registrationService;
  private TokenService tokenService;
  private TwoFactorService twoFactorService;

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, reads the
   * configuration, obtains a proxy for the Event bus services exposed through service discovery,
   * start an HTTPs server at port 8443 or an HTTP server at port 8080.
   * 
   * @throws Exception which is a startup exception TODO Need to add documentation for all the
   * 
   */

  @Override
  public void start() throws Exception {

    Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add(HEADER_ACCEPT);
    allowedHeaders.add(HEADER_TOKEN);
    allowedHeaders.add(HEADER_CONTENT_LENGTH);
    allowedHeaders.add(HEADER_CONTENT_TYPE);
    allowedHeaders.add(HEADER_HOST);
    allowedHeaders.add(HEADER_ORIGIN);
    allowedHeaders.add(HEADER_REFERER);
    allowedHeaders.add(HEADER_ALLOW_ORIGIN);

    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    allowedMethods.add(HttpMethod.DELETE);
    allowedMethods.add(HttpMethod.PATCH);
    allowedMethods.add(HttpMethod.PUT);

    /* Create a reference to HazelcastClusterManager. */

    router = Router.router(vertx);

    /* Define the APIs, methods, endpoints and associated methods. */

    router = Router.router(vertx);
    router.route().handler(
        CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    router.route().handler(BodyHandler.create());

    /**
     * Documentation routes
     */
    /* Static Resource Handler */
    /* Get openapiv3 spec */
    router.get(ROUTE_STATIC_SPEC).produces(MIME_APPLICATION_JSON).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/openapi.yaml");
    });
    /* Get redoc */
    router.get(ROUTE_DOC).produces(MIME_TEXT_HTML).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/apidoc.html");
    });

    /* Read ssl configuration. */
    isSSL = config().getBoolean("ssl");
    HttpServerOptions serverOptions = new HttpServerOptions();

    if (isSSL) {
      LOGGER.debug("Info: Starting HTTPs server");

      /* Read the configuration and set the HTTPs server properties. */

      keystore = config().getString("keystore");
      keystorePassword = config().getString("keystorePassword");

      /* Setup the HTTPs server properties, APIs and port. */

      serverOptions.setSsl(true)
          .setKeyStoreOptions(new JksOptions().setPath(keystore).setPassword(keystorePassword));

    } else {
      LOGGER.debug("Info: Starting HTTP server");

      /* Setup the HTTP server properties, APIs and port. */

      serverOptions.setSsl(false);
    }

    serverOptions.setCompressionSupported(true).setCompressionLevel(5);
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(router).listen(port);

    /* Get a handler for the Service Discovery interface. */

    tipService = TIPService.createProxy(vertx, TIP_SERVICE_ADDRESS);
    policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    tokenService = TokenService.createProxy(vertx, TOKEN_SERVICE_ADDRESS);
    twoFactorService = TwoFactorService.createProxy(vertx, TWOFACTOR_SERVICE_ADDRESS);
  }

}
