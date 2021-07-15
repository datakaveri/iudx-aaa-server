package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.util.Constants.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.http.client.protocol.RequestAuthCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.RequestAuthentication;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.token.TokenService;
import iudx.aaa.server.admin.AdminService;

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
  
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;

  /** Service addresses */
  private static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private static final String ADMIN_SERVICE_ADDRESS = "iudx.aaa.admin.service";

  private PolicyService policyService;
  private RegistrationService registrationService;
  private TokenService tokenService;
  private AdminService adminService;

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
    
    databaseIP = config().getString(DATABASE_IP);
    databasePort = Integer.parseInt(config().getString(DATABASE_PORT));
    databaseName = config().getString(DATABASE_NAME);
    databaseUserName = config().getString(DATABASE_USERNAME);
    databasePassword = config().getString(DATABASE_PASSWORD);
    poolSize = Integer.parseInt(config().getString(POOLSIZE));
    keystorePassword = config().getString(KEYSTPRE_PASSWORD);
    JsonObject keycloakOptions = config().getJsonObject(KEYCLOACK_OPTIONS);
    
    /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setConnectTimeout(PG_CONNECTION_TIMEOUT);
    }
    
    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }
    
    PgPool pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

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
    
    RequestAuthentication reqAuth = new RequestAuthentication(vertx, pgPool,keycloakOptions);
    
    //Create token
    router.post(API_TOKEN).consumes(MIME_APPLICATION_JSON)
    .handler(reqAuth)
    .handler(this::createTokenHandler);
    
    //Revoke Token
    router.post(API_REVOKE_TOKEN).consumes(MIME_APPLICATION_JSON)
    .handler(this::revokeTokenHandler);
    
    //Introspect token
    router.post(API_INTROSPECT_TOKEN).consumes(MIME_APPLICATION_JSON)
    .handler(this::validateTokenHandler);

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
    policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    tokenService = TokenService.createProxy(vertx, TOKEN_SERVICE_ADDRESS);
    adminService = AdminService.createProxy(vertx, ADMIN_SERVICE_ADDRESS);
  }
  
  
  /**
   * Handler to handle create token request.
   * 
   * @param context which is RoutingContext
   */
  private void createTokenHandler(RoutingContext context) {
    JsonObject tokenRequestJson = context.getBodyAsJson();
    
    RequestToken requestTokenDTO = tokenRequestJson.mapTo(RequestToken.class);
    
    List<Roles> role1 = new ArrayList<Roles>(Arrays.asList(Roles.values()));
    List<Roles> role = Arrays.asList(Roles.values());
    User.UserBuilder a = new UserBuilder().roles(role);
    User user = new User(a);
    
    tokenService.createToken(requestTokenDTO, handler -> {
      if (handler.succeeded()) {
        context.response().putHeader("content-type", "application/json").setStatusCode(200)
        .end(handler.result().toString());
      } else {
        handler.cause().printStackTrace();
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }
  
  //TODO: For test purpose; Stub code
  private void validateTokenHandler(RoutingContext context) {
    JsonObject tokenRequestJson = context.getBodyAsJson();
    IntrospectToken introspectToken = tokenRequestJson.mapTo(IntrospectToken.class);
    
    tokenService.validateToken(introspectToken, handler -> {
      if (handler.succeeded()) {
        context.response().putHeader("content-type", "application/json").setStatusCode(200)
        .end(handler.result().toString());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getLocalizedMessage());
      }
    });
  }
  
  //TODO: For test purpose; Stub code
  private void revokeTokenHandler(RoutingContext context) {
    
    UUID usrId = UUID.fromString("32a4b979-4f4a-4c44-b0c3-2fe109952b5f");
    User.UserBuilder a = new UserBuilder().userId(usrId);
    User user = new User(a);
    
    JsonObject tokenRequestJson = context.getBodyAsJson();
    RevokeToken revokeToken = tokenRequestJson.mapTo(RevokeToken.class);
    tokenService.revokeToken(revokeToken, user, handler -> {
      if (handler.succeeded()) {
        context.response().putHeader("content-type", "application/json").setStatusCode(200)
        .end(handler.result().toString());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getLocalizedMessage());
      }
    });
  }
}
