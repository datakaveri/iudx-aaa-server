package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.util.Constants.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
import iudx.aaa.server.admin.AdminService;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.util.FailureHandler;
import iudx.aaa.server.apiserver.util.RequestAuthentication;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.token.TokenService;

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

    RequestAuthentication reqAuth = new RequestAuthentication(vertx, pgPool, keycloakOptions);
    FailureHandler failureHandler = new FailureHandler();

    // Create token
    router.post(API_TOKEN).consumes(MIME_APPLICATION_JSON).handler(reqAuth)
        .handler(this::createTokenHandler).failureHandler(failureHandler);

    // Revoke Token
    router.post(API_REVOKE_TOKEN).consumes(MIME_APPLICATION_JSON).handler(reqAuth)
        .handler(this::revokeTokenHandler).failureHandler(failureHandler);

    // Introspect token
    router.post(API_INTROSPECT_TOKEN).consumes(MIME_APPLICATION_JSON)
        .handler(this::validateTokenHandler).failureHandler(failureHandler);

    // Create user profile
    router.post(API_USER_PROFILE).handler(reqAuth).handler(this::createUserProfile)
        .failureHandler(failureHandler);

    // List user profile
    router.get(API_USER_PROFILE).handler(reqAuth).handler(this::listUserProfile)
        .failureHandler(failureHandler);

    // Update user
    router.put(API_USER_PROFILE).consumes(MIME_APPLICATION_JSON).handler(reqAuth)
        .handler(this::updateUserProfile).failureHandler(failureHandler);

    // List organizations
    router.get(API_ORGANIZATION).handler(reqAuth).handler(this::listOrganization)
        .failureHandler(failureHandler);

    // Admin create organization
    router.post(API_ORGANIZATION).consumes(MIME_APPLICATION_JSON).handler(reqAuth)
        .handler(this::adminCreateOrganization).failureHandler(failureHandler);

    // Admin list provider reg
    router.post(API_ADMIN_PROVIDER_REG).handler(reqAuth).handler(this::adminGetProviderReg)
        .failureHandler(failureHandler);

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
    isSSL = config().getBoolean(SSL);
    HttpServerOptions serverOptions = new HttpServerOptions();

    if (isSSL) {
      LOGGER.debug("Info: Starting HTTPs server");

      /* Read the configuration and set the HTTPs server properties. */
      keystore = config().getString(KEYSTORE_PATH);
      keystorePassword = config().getString(KEYSTPRE_PASSWORD);

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

    /* Mapping request body to Object */
    JsonObject tokenRequestJson = context.getBodyAsJson();
    tokenRequestJson.put(CLIENT_ID, context.get(CLIENT_ID));
    RequestToken requestTokenDTO = tokenRequestJson.mapTo(RequestToken.class);
    User user = context.get(USER);

    tokenService.createToken(requestTokenDTO, user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handle the Token Introspection.
   * 
   * @param context
   */
  private void validateTokenHandler(RoutingContext context) {
    JsonObject tokenRequestJson = context.getBodyAsJson();
    IntrospectToken introspectToken = tokenRequestJson.mapTo(IntrospectToken.class);

    tokenService.validateToken(introspectToken, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles the Token revocation.
   * 
   * @param context
   */
  private void revokeTokenHandler(RoutingContext context) {

  }

  /**
   * Handles user profile creation.
   * 
   * @param context
   */
  private void createUserProfile(RoutingContext context) {
    JsonObject jsonRequest = context.getBodyAsJson();
    RegistrationRequest request = RegistrationRequest.validatedObj(jsonRequest);
    User user = context.get(USER);

    registrationService.createUser(request, user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles listing user profile.
   * 
   * @param context
   */
  private void listUserProfile(RoutingContext context) {
    User user = context.get(USER);

    registrationService.listUser(user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles user profile update.
   * 
   * @param context
   */
  private void updateUserProfile(RoutingContext context) {
    JsonObject jsonRequest = context.getBodyAsJson();
    UpdateProfileRequest request = UpdateProfileRequest.validatedObj(jsonRequest);
    User user = context.get(USER);

    registrationService.updateUser(request, user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });

  }

  /**
   * Handles organization listing.
   * 
   * @param context
   */
  private void listOrganization(RoutingContext context) {
    registrationService.listOrganization(handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles org creation by admin user.
   * 
   * @param context
   */
  private void adminCreateOrganization(RoutingContext context) {
    JsonObject jsonRequest = context.getBodyAsJson();
    CreateOrgRequest request = CreateOrgRequest.validatedObj(jsonRequest);
    User user = context.get(USER);

    adminService.createOrganization(request, user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles list provider registrations by admin.
   * 
   * @param context
   */
  private void adminGetProviderReg(RoutingContext context) {
    List<String> filterList = context.queryParam(QUERY_FILTER);
    RoleStatus filter;

    if (filterList.size() > 0) {
      String value = filterList.get(0).toUpperCase();
      if (RoleStatus.exists(value)) {
        filter = RoleStatus.valueOf(value);
      } else {
        throw new IllegalArgumentException(ERR_DETAIL_BAD_FILTER);
      }
    } else {
      filter = RoleStatus.PENDING;
    }

    User user = context.get(USER);
    adminService.getProviderRegistrations(filter, user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  private Future<Void> processResponse(HttpServerResponse response, JsonObject msg) {
    int status = msg.getInteger(STATUS, 400);
    msg.remove(STATUS);
    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);
    return response.setStatusCode(status).end(msg.toString());
  }

  private Future<Void> processResponse(HttpServerResponse response, String msg) {
    Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).detail(msg).build();
    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);
    return response.setStatusCode(500).end(rs.toJsonString());
  }
}
