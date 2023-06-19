package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.util.Constants.API;
import static iudx.aaa.server.apiserver.util.Constants.AUTHSERVER_DOMAIN;
import static iudx.aaa.server.apiserver.util.Constants.BODY;
import static iudx.aaa.server.apiserver.util.Constants.CERTIFICATE;
import static iudx.aaa.server.apiserver.util.Constants.CLIENT_ID;
import static iudx.aaa.server.apiserver.util.Constants.CONTEXT_SEARCH_USER;
import static iudx.aaa.server.apiserver.util.Constants.CORS_REGEX;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_APD;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_DELEGATIONS;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_ORGANIZATIONS;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_POLICIES;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.CREATE_USER_PROFILE;
import static iudx.aaa.server.apiserver.util.Constants.DATA;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_IP;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_NAME;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_PASSWORD;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_PORT;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_SCHEMA;
import static iudx.aaa.server.apiserver.util.Constants.DATABASE_USERNAME;
import static iudx.aaa.server.apiserver.util.Constants.DB_RECONNECT_ATTEMPTS;
import static iudx.aaa.server.apiserver.util.Constants.DB_RECONNECT_INTERVAL_MS;
import static iudx.aaa.server.apiserver.util.Constants.DELETE_DELEGATIONS;
import static iudx.aaa.server.apiserver.util.Constants.DELETE_POLICIES;
import static iudx.aaa.server.apiserver.util.Constants.DELETE_POLICIES_REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.ERR_AUDIT_UPDATE;
import static iudx.aaa.server.apiserver.util.Constants.ERR_DETAIL_BAD_FILTER;
import static iudx.aaa.server.apiserver.util.Constants.GET_CERT;
import static iudx.aaa.server.apiserver.util.Constants.GET_DELEGATIONS;
import static iudx.aaa.server.apiserver.util.Constants.GET_JWKS;
import static iudx.aaa.server.apiserver.util.Constants.GET_ORGANIZATIONS;
import static iudx.aaa.server.apiserver.util.Constants.GET_POLICIES;
import static iudx.aaa.server.apiserver.util.Constants.GET_POLICIES_REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.GET_PVDR_REGISTRATION;
import static iudx.aaa.server.apiserver.util.Constants.GET_USER_PROFILE;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ACCEPT;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ALLOW_ORIGIN;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_AUTHORIZATION;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_CONTENT_LENGTH;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_CONTENT_TYPE;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_EMAIL;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_HOST;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ORIGIN;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_PROVIDER_ID;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_REFERER;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ROLE;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_X_CONTENT_TYPE_OPTIONS;
import static iudx.aaa.server.apiserver.util.Constants.INTERNAL_SVR_ERR;
import static iudx.aaa.server.apiserver.util.Constants.JSON_NOT_FOUND;
import static iudx.aaa.server.apiserver.util.Constants.KEYCLOACK_OPTIONS;
import static iudx.aaa.server.apiserver.util.Constants.KEYSTORE_PATH;
import static iudx.aaa.server.apiserver.util.Constants.KEYSTPRE_PASSWORD;
import static iudx.aaa.server.apiserver.util.Constants.KS_ALIAS;
import static iudx.aaa.server.apiserver.util.Constants.KS_PARSE_ERROR;
import static iudx.aaa.server.apiserver.util.Constants.LIST_APD;
import static iudx.aaa.server.apiserver.util.Constants.METHOD;
import static iudx.aaa.server.apiserver.util.Constants.MIME_APPLICATION_JSON;
import static iudx.aaa.server.apiserver.util.Constants.MIME_TEXT_HTML;
import static iudx.aaa.server.apiserver.util.Constants.PG_CONNECTION_TIMEOUT;
import static iudx.aaa.server.apiserver.util.Constants.POOLSIZE;
import static iudx.aaa.server.apiserver.util.Constants.POST_POLICIES_REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.PUT_POLICIES_REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.QUERY_FILTER;
import static iudx.aaa.server.apiserver.util.Constants.REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.REVOKE_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.ROUTE_DOC;
import static iudx.aaa.server.apiserver.util.Constants.ROUTE_STATIC_SPEC;
import static iudx.aaa.server.apiserver.util.Constants.SERVER_TIMEOUT_MS;
import static iudx.aaa.server.apiserver.util.Constants.STATUS;
import static iudx.aaa.server.apiserver.util.Constants.SUCC_AUDIT_UPDATE;
import static iudx.aaa.server.apiserver.util.Constants.TIP_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.UPDATE_APD;
import static iudx.aaa.server.apiserver.util.Constants.UPDATE_PVDR_REGISTRATION;
import static iudx.aaa.server.apiserver.util.Constants.UPDATE_USER_PROFILE;
import static iudx.aaa.server.apiserver.util.Constants.USER;
import static iudx.aaa.server.apiserver.util.Constants.USER_ID;
import static iudx.aaa.server.apiserver.util.Constants.X_CONTENT_TYPE_OPTIONS_NOSNIFF;
import static iudx.aaa.server.apiserver.util.Constants.successStatus;

import com.nimbusds.jose.jwk.ECKey;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.admin.AdminService;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.util.ClientAuthentication;
import iudx.aaa.server.apiserver.util.FailureHandler;
import iudx.aaa.server.apiserver.util.OIDCAuthentication;
import iudx.aaa.server.apiserver.util.ProviderAuthentication;
import iudx.aaa.server.apiserver.util.SearchUserHandler;
import iudx.aaa.server.auditing.AuditingService;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.token.TokenService;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The AAA Server API Verticle.
 *
 * <h1>AAA Server API Verticle</h1>
 *
 * <p>The API Server verticle implements the IUDX AAA Server APIs. It handles the API requests from
 * the clients and interacts with the associated Service to respond.
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
  /** Service addresses */
  private static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";

  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private static final String ADMIN_SERVICE_ADDRESS = "iudx.aaa.admin.service";
  private static final String AUDITING_SERVICE_ADDRESS = "iudx.aaa.auditing.service";
  private static final String APD_SERVICE_ADDRESS = "iudx.aaa.apd.service";
  private HttpServer server;
  private Router router;
  private int port;
  private String jwtKeystorePath;
  private String jwtKeystorePassword;
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseSchema;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;
  private long serverTimeout;
  private String corsRegex;
  private String authServerDomain;
  private PolicyService policyService;
  private RegistrationService registrationService;
  private TokenService tokenService;
  private AdminService adminService;
  private AuditingService auditingService;
  private ApdService apdService;

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, reads the
   * configuration, obtains a proxy for the Event bus services exposed through service discovery,
   * start an HTTP server at port 8080 unless another port is supplied in the configuration.
   *
   * @throws Exception which is a startup exception TODO Need to add documentation for all the
   */
  @Override
  public void start() throws Exception {

    databaseIP = config().getString(DATABASE_IP);
    databasePort = Integer.parseInt(config().getString(DATABASE_PORT));
    databaseName = config().getString(DATABASE_NAME);
    databaseSchema = config().getString(DATABASE_SCHEMA);
    databaseUserName = config().getString(DATABASE_USERNAME);
    databasePassword = config().getString(DATABASE_PASSWORD);
    poolSize = Integer.parseInt(config().getString(POOLSIZE));
    JsonObject keycloakOptions = config().getJsonObject(KEYCLOACK_OPTIONS);
    serverTimeout = Long.parseLong(config().getString(SERVER_TIMEOUT_MS));
    corsRegex = config().getString(CORS_REGEX);
    authServerDomain = config().getString(AUTHSERVER_DOMAIN);
    jwtKeystorePath = config().getString(KEYSTORE_PATH);
    jwtKeystorePassword = config().getString(KEYSTPRE_PASSWORD);

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
              .setConnectTimeout(PG_CONNECTION_TIMEOUT)
              .setProperties(schemaProp)
              .setReconnectAttempts(DB_RECONNECT_ATTEMPTS)
              .setReconnectInterval(DB_RECONNECT_INTERVAL_MS);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    PgPool pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

    Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add(HEADER_ACCEPT);
    allowedHeaders.add(HEADER_AUTHORIZATION);
    allowedHeaders.add(HEADER_CONTENT_LENGTH);
    allowedHeaders.add(HEADER_CONTENT_TYPE);
    allowedHeaders.add(HEADER_HOST);
    allowedHeaders.add(HEADER_ORIGIN);
    allowedHeaders.add(HEADER_REFERER);
    allowedHeaders.add(HEADER_ALLOW_ORIGIN);

    allowedHeaders.add(HEADER_PROVIDER_ID);
    allowedHeaders.add(HEADER_EMAIL);
    allowedHeaders.add(HEADER_ROLE);

    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    allowedMethods.add(HttpMethod.DELETE);
    allowedMethods.add(HttpMethod.PATCH);
    allowedMethods.add(HttpMethod.PUT);

    /* Passing the full config to OIDC auth, as the config has all the required keycloak
     * options */
    OIDCAuthentication oidcFlow = new OIDCAuthentication(vertx, pgPool, config());
    ClientAuthentication clientFlow = new ClientAuthentication(pgPool);
    ProviderAuthentication providerAuth = new ProviderAuthentication(pgPool, authServerDomain);
    SearchUserHandler searchUser = new SearchUserHandler();
    FailureHandler failureHandler = new FailureHandler();

    RouterBuilder.create(vertx, "docs/openapi.yaml")
        .onFailure(Throwable::printStackTrace)
        .onSuccess(
            routerBuilder -> {
              LOGGER.debug("Info: Mouting routes from OpenApi3 spec");

              RouterBuilderOptions factoryOptions =
                  new RouterBuilderOptions().setMountResponseContentTypeHandler(true);
              //  .setRequireSecurityHandlers(false);
              routerBuilder.setOptions(factoryOptions);
              routerBuilder.securityHandler("authorization", oidcFlow);

              // Post token create
              routerBuilder
                  .operation(CREATE_TOKEN)
                  .handler(clientFlow)
                  .handler(this::createTokenHandler)
                  .failureHandler(failureHandler);

              // Post token introspect
              routerBuilder
                  .operation(TIP_TOKEN)
                  .handler(this::validateTokenHandler)
                  .failureHandler(failureHandler);

              // Post token revoke
              routerBuilder
                  .operation(REVOKE_TOKEN)
                  .handler(this::revokeTokenHandler)
                  .failureHandler(failureHandler);

              // Post user profile
              routerBuilder
                  .operation(CREATE_USER_PROFILE)
                  .handler(this::createUserProfileHandler)
                  .failureHandler(failureHandler);

              // Get user profile
              routerBuilder
                  .operation(GET_USER_PROFILE)
                  .handler(providerAuth)
                  .handler(searchUser)
                  .handler(this::listUserProfileHandler)
                  .failureHandler(failureHandler);

              routerBuilder
                  .operation(UPDATE_USER_PROFILE)
                  .handler(this::updateUserProfileHandler)
                  .failureHandler(failureHandler);

              // Get Organization Details
              routerBuilder
                  .operation(GET_ORGANIZATIONS)
                  .handler(this::listOrganizationHandler)
                  .failureHandler(failureHandler);

              // Post Create Organization
              routerBuilder
                  .operation(CREATE_ORGANIZATIONS)
                  .handler(this::adminCreateOrganizationHandler)
                  .failureHandler(failureHandler);

              // Get Provider registrations
              routerBuilder
                  .operation(GET_PVDR_REGISTRATION)
                  .handler(this::adminGetProviderRegHandler)
                  .failureHandler(failureHandler);

              // Update Provider registration status
              routerBuilder
                  .operation(UPDATE_PVDR_REGISTRATION)
                  .handler(this::adminUpdateProviderRegHandler)
                  .failureHandler(failureHandler);

              // Get/lists the User policies
              routerBuilder
                  .operation(GET_POLICIES)
                  .handler(providerAuth)
                  .handler(this::listPolicyHandler)
                  .failureHandler(failureHandler);

              // Create a new User policies
              routerBuilder
                  .operation(CREATE_POLICIES)
                  .handler(providerAuth)
                  .handler(this::createPolicyHandler)
                  .failureHandler(failureHandler);

              // Delete a User policies
              routerBuilder
                  .operation(DELETE_POLICIES)
                  .handler(providerAuth)
                  .handler(this::deletePolicyHandler)
                  .failureHandler(failureHandler);

              // Lists all the policies related requests for user/provider
              routerBuilder
                  .operation(GET_POLICIES_REQUEST)
                  .handler(providerAuth)
                  .handler(this::getPolicyNotificationHandler)
                  .failureHandler(failureHandler);

              // Creates new policy request for user
              routerBuilder
                  .operation(POST_POLICIES_REQUEST)
                  .handler(this::createPolicyNotificationHandler)
                  .failureHandler(failureHandler);

              // Updates the policy request by provider/delegate
              routerBuilder
                  .operation(PUT_POLICIES_REQUEST)
                  .handler(providerAuth)
                  .handler(this::updatePolicyNotificationHandler)
                  .failureHandler(failureHandler);

              // deletes the policy request by consumer
              routerBuilder
                  .operation(DELETE_POLICIES_REQUEST)
                  .handler(this::deleteNotificationHandler)
                  .failureHandler(failureHandler);

              // Get delegations by provider/delegate/auth delegate
              routerBuilder
                  .operation(GET_DELEGATIONS)
                  .handler(providerAuth)
                  .handler(this::listDelegationsHandler)
                  .failureHandler(failureHandler);

              // Delete delegations by provider/delegate/auth delegate
              routerBuilder
                  .operation(DELETE_DELEGATIONS)
                  .handler(providerAuth)
                  .handler(this::deleteDelegationsHandler)
                  .failureHandler(failureHandler);

              // Create delegations
              routerBuilder
                  .operation(CREATE_DELEGATIONS)
                  .handler(providerAuth)
                  .handler(this::createDelegationsHandler)
                  .failureHandler(failureHandler);

              // Create APD
              routerBuilder
                  .operation(CREATE_APD)
                  .handler(this::createApdHandler)
                  .failureHandler(failureHandler);

              // Update APD status
              routerBuilder
                  .operation(UPDATE_APD)
                  .handler(this::updateApdHandler)
                  .failureHandler(failureHandler);

              // List APDs
              routerBuilder
                  .operation(LIST_APD)
                  .handler(this::listApdHandler)
                  .failureHandler(failureHandler);

              // Get PublicKey
              routerBuilder.operation(GET_CERT).handler(this::pubCertHandler);
              // Get PublicKey in JWKS format
              routerBuilder.operation(GET_JWKS).handler(this::retrievePublicKey);

              /* TimeoutHandler needs to be added as rootHandler */
              routerBuilder.rootHandler(TimeoutHandler.create(serverTimeout));

              // Router configuration- CORS, methods and headers
              routerBuilder.rootHandler(
                  CorsHandler.create(corsRegex)
                      .allowedHeaders(allowedHeaders)
                      .allowedMethods(allowedMethods));

              routerBuilder.rootHandler(BodyHandler.create());

              router = routerBuilder.createRouter();

              // Static Resource Handler.Get openapiv3 spec
              router
                  .get(ROUTE_STATIC_SPEC)
                  .produces(MIME_APPLICATION_JSON)
                  .handler(
                      routingContext -> {
                        HttpServerResponse response = routingContext.response();
                        response.sendFile("docs/openapi.yaml");
                      });

              // Get redoc
              router
                  .get(ROUTE_DOC)
                  .produces(MIME_TEXT_HTML)
                  .handler(
                      routingContext -> {
                        HttpServerResponse response = routingContext.response();
                        response.sendFile("docs/apidoc.html");
                      });

              /* In case API/method not implemented, this last route is triggered */
              router
                  .route()
                  .last()
                  .handler(
                      routingContext -> {
                        HttpServerResponse response = routingContext.response();
                        response
                            .putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
                            .setStatusCode(404)
                            .end(JSON_NOT_FOUND);
                      });

              HttpServerOptions serverOptions = new HttpServerOptions();
              LOGGER.debug("Info: Starting HTTP server");

              /*
               * Setup the HTTP server properties, APIs and port. Default port is 8080. If set through
               * config, then that value is taken
               */
              serverOptions.setSsl(false);
              port =
                  config().getInteger("httpPort") == null ? 8080 : config().getInteger("httpPort");
              LOGGER.info("Info: Starting HTTP server at port " + port);

              serverOptions.setCompressionSupported(true).setCompressionLevel(5);
              server = vertx.createHttpServer(serverOptions);
              server
                  .requestHandler(router)
                  .listen(port)
                  .onSuccess(
                      success -> {
                        LOGGER.debug("Info: Started HTTP server");
                      })
                  .onFailure(
                      err -> {
                        LOGGER.fatal("Info: Failed to start HTTP server - " + err.getMessage());
                      });

              /* Get a handler for the Service Discovery interface. */
              policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
              registrationService =
                  RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
              tokenService = TokenService.createProxy(vertx, TOKEN_SERVICE_ADDRESS);
              adminService = AdminService.createProxy(vertx, ADMIN_SERVICE_ADDRESS);
              auditingService = AuditingService.createProxy(vertx, AUDITING_SERVICE_ADDRESS);
              apdService = ApdService.createProxy(vertx, APD_SERVICE_ADDRESS);
            });
  }

  /**
   * Handler to handle create token request.
   *
   * @param context which is RoutingContext
   */
  private void createTokenHandler(RoutingContext context) {

    /* Mapping request body to Object */
    JsonObject tokenRequestJson = context.body().asJsonObject();
    tokenRequestJson.put(CLIENT_ID, context.get(CLIENT_ID));
    RequestToken requestTokenDTO = new RequestToken(tokenRequestJson);
    User user = context.get(USER);

    tokenService.createToken(
        requestTokenDTO,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), result);
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
    JsonObject tokenRequestJson = context.body().asJsonObject();
    IntrospectToken introspectToken = tokenRequestJson.mapTo(IntrospectToken.class);

    tokenService.validateToken(
        introspectToken,
        handler -> {
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
    /* Mapping request body to Object */
    JsonObject tokenRequestJson = context.body().asJsonObject();
    RevokeToken revokeTokenDTO = tokenRequestJson.mapTo(RevokeToken.class);

    User user = context.get(USER);

    tokenService.revokeToken(
        revokeTokenDTO,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Handles user profile creation.
   *
   * @param context
   */
  private void createUserProfileHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    RegistrationRequest request = new RegistrationRequest(jsonRequest);
    User user = context.get(USER);

    registrationService.createUser(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
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
  private void listUserProfileHandler(RoutingContext context) {
    User user = context.get(USER);

    JsonObject authDelegateDetails =
        Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    JsonObject searchUserDetails =
        Optional.ofNullable((JsonObject) context.get(CONTEXT_SEARCH_USER)).orElse(new JsonObject());

    registrationService.listUser(
        user,
        searchUserDetails,
        authDelegateDetails,
        handler -> {
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
  private void updateUserProfileHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    UpdateProfileRequest request = new UpdateProfileRequest(jsonRequest);
    User user = context.get(USER);

    registrationService.updateUser(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
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
  private void listOrganizationHandler(RoutingContext context) {
    registrationService.listOrganization(
        handler -> {
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
  private void adminCreateOrganizationHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    CreateOrgRequest request = new CreateOrgRequest(jsonRequest);
    User user = context.get(USER);

    adminService.createOrganization(
        request,
        user,
        handler -> {
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
  private void adminGetProviderRegHandler(RoutingContext context) {
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
    adminService.getProviderRegistrations(
        filter,
        user,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Handles update provider registrations by admin.
   *
   * @param context
   */
  private void adminUpdateProviderRegHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    JsonArray arr = jsonRequest.getJsonArray(REQUEST);
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(arr);

    User user = context.get(USER);
    adminService.updateProviderRegistrationStatus(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Lists Policy associated with a User.
   *
   * @param context
   */
  private void listPolicyHandler(RoutingContext context) {
    User user = context.get(USER);
    JsonObject data = Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    policyService.listPolicy(
        user,
        data,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Create a Policy for a User.
   *
   * @param context
   */
  private void createPolicyHandler(RoutingContext context) {

    JsonObject arr = context.body().asJsonObject();
    JsonArray jsonRequest = arr.getJsonArray(REQUEST);
    List<CreatePolicyRequest> request = CreatePolicyRequest.jsonArrayToList(jsonRequest);
    User user = context.get(USER);
    JsonObject data = Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());
    policyService.createPolicy(
        request,
        user,
        data,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Delete a policy assoicated with a User.
   *
   * @param context
   */
  private void deletePolicyHandler(RoutingContext context) {

    JsonObject arr = context.body().asJsonObject();
    JsonArray jsonRequest = arr.getJsonArray(REQUEST);
    List<DeletePolicyRequest> request = DeletePolicyRequest.jsonArrayToList(jsonRequest);
    User user = context.get(USER);
    JsonObject data = Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());
    policyService.deletePolicy(
        jsonRequest,
        user,
        data,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Get all the resource access requests by user to provider/delegate.
   *
   * @param context
   */
  private void getPolicyNotificationHandler(RoutingContext context) {

    User user = context.get(USER);
    JsonObject data = Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    policyService.listPolicyNotification(
        user,
        data,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Create the resource access requests by user to provider/delegate.
   *
   * @param context
   */
  private void createPolicyNotificationHandler(RoutingContext context) {

    JsonArray jsonRequest = context.body().asJsonObject().getJsonArray(REQUEST);
    List<CreatePolicyNotification> request = CreatePolicyNotification.jsonArrayToList(jsonRequest);
    User user = context.get(USER);

    policyService.createPolicyNotification(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Update the access status, expiry of resources by provider/delegate for user.
   *
   * @param context
   */
  private void updatePolicyNotificationHandler(RoutingContext context) {

    JsonArray jsonRequest = context.body().asJsonObject().getJsonArray(REQUEST);
    List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(jsonRequest);
    JsonObject data = Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());
    User user = context.get(USER);

    policyService.updatePolicyNotification(
        request,
        user,
        data,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Delete a notification created by a User.
   *
   * @param context
   */
  private void deleteNotificationHandler(RoutingContext context) {

    JsonObject arr = context.body().asJsonObject();
    JsonArray jsonRequest = arr.getJsonArray(REQUEST);
    List<DeletePolicyNotificationRequest> request =
        DeletePolicyNotificationRequest.jsonArrayToList(jsonRequest);
    User user = context.get(USER);
    policyService.deletePolicyNotification(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * List delegations for provider/delegate/auth delegate
   *
   * @param context
   */
  private void listDelegationsHandler(RoutingContext context) {

    User user = context.get(USER);
    JsonObject authDelegateDetails =
        Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    policyService.listDelegation(
        user,
        authDelegateDetails,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * @param context
   */
  private void deleteDelegationsHandler(RoutingContext context) {

    JsonObject jsonRequest = context.body().asJsonObject();
    JsonArray arr = jsonRequest.getJsonArray(REQUEST);
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(arr);

    User user = context.get(USER);
    JsonObject authDelegateDetails =
        Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    policyService.deleteDelegation(
        request,
        user,
        authDelegateDetails,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Create a delegation for a User.
   *
   * @param context
   */
  private void createDelegationsHandler(RoutingContext context) {

    JsonObject arr = context.body().asJsonObject();
    JsonArray jsonRequest = arr.getJsonArray(REQUEST);
    List<CreateDelegationRequest> request = CreateDelegationRequest.jsonArrayToList(jsonRequest);
    User user = context.get(USER);
    JsonObject authDelegateDetails =
        Optional.ofNullable((JsonObject) context.get(DATA)).orElse(new JsonObject());

    policyService.createDelegation(
        request,
        user,
        authDelegateDetails,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Create an Access Policy Domain (APD).
   *
   * @param context
   */
  private void createApdHandler(RoutingContext context) {

    JsonObject jsonRequest = context.body().asJsonObject();
    CreateApdRequest request = new CreateApdRequest(jsonRequest);
    User user = context.get(USER);

    apdService.createApd(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * List Access Policy Domains (APDs).
   *
   * @param context
   */
  private void listApdHandler(RoutingContext context) {

    User user = context.get(USER);

    apdService.listApd(
        user,
        handler -> {
          if (handler.succeeded()) {
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Update status of Access Policy Domains (APDs).
   *
   * @param context
   */
  private void updateApdHandler(RoutingContext context) {

    JsonObject arr = context.body().asJsonObject();
    JsonArray jsonRequest = arr.getJsonArray(REQUEST);
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(jsonRequest);
    User user = context.get(USER);

    apdService.updateApd(
        request,
        user,
        handler -> {
          if (handler.succeeded()) {
            JsonObject result = handler.result();
            Future.future(future -> handleAuditLogs(context, result));
            processResponse(context.response(), handler.result());
          } else {
            processResponse(context.response(), handler.cause().getLocalizedMessage());
          }
        });
  }

  /**
   * Lists JWT signing public key.
   *
   * @param context
   */
  private void pubCertHandler(RoutingContext context) {

    JksOptions options = new JksOptions().setPath(jwtKeystorePath).setPassword(jwtKeystorePassword);
    try {
      KeyStore ks = options.loadKeyStore(vertx);
      if (ks.containsAlias(KS_ALIAS)) {
        Certificate cert = ks.getCertificate(KS_ALIAS);
        String pubKeyCertEncoded = Base64.encodeBase64String(cert.getEncoded());
        String certKeyString =
            "-----BEGIN CERTIFICATE-----\n" + pubKeyCertEncoded + "\n-----END CERTIFICATE-----";

        context
            .response()
            .putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
            .end(new JsonObject().put(CERTIFICATE, certKeyString).encode());

      } else {
        processResponse(context.response(), KS_PARSE_ERROR);
      }
    } catch (Exception e) {
      processResponse(context.response(), KS_PARSE_ERROR);
    }
  }

  /**
   * Loads the keystore using the provided path and password, and retrieves the public key
   * information.
   *
   * @param context The routing context
   */
  private void retrievePublicKey(RoutingContext context) {

    JksOptions options = new JksOptions().setPath(jwtKeystorePath).setPassword(jwtKeystorePassword);
    try {
      KeyStore ks = options.loadKeyStore(vertx);
      {
        ECKey ecKey = ECKey.load(ks, "ES256", jwtKeystorePassword.toCharArray());
        JsonArray result = new JsonArray().add(ecKey.toPublicJWK().toJSONObject());
        context
            .response()
            .putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
            .end(new JsonObject().put("keys", result).encode());
      }
    } catch (Exception e) {
      processResponse(context.response(), KS_PARSE_ERROR);
    }
  }

  /**
   * HTTP Response Wrapper
   *
   * @param response
   * @param msg JsonObject
   * @return context response
   */
  private Future<Void> processResponse(HttpServerResponse response, JsonObject msg) {
    int status = msg.getInteger(STATUS, 400);
    msg.remove(STATUS);

    /* In case of a timeout, the response may already be sent */
    if (response.ended()) {
      LOGGER.warn("Trying to send processed API response after timeout");
      return Future.succeededFuture();
    }
    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);
    response.putHeader(HEADER_X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_NOSNIFF);

    return response.setStatusCode(status).end(msg.toString());
  }

  private Future<Void> processResponse(HttpServerResponse response, String msg) {
    Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).detail(msg).build();
    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);
    response.putHeader(HEADER_X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_NOSNIFF);
    return response.setStatusCode(500).end(rs.toJsonString());
  }

  /**
   * Handles succeeded audit logs. Creates the required {@link JsonObject} from the {@link
   * RoutingContext} and pass on to it {@link AuditingService} for database storage when the
   * requests succeeded. Applicable to POST, PUT and DELETE HTTP operations.
   *
   * @param context which is {@link RoutingContext}
   * @return void which is {@link Future}
   */
  private Future<Void> handleAuditLogs(RoutingContext context, JsonObject result) {

    Promise<Void> promise = Promise.promise();

    int status = result.getInteger(STATUS, 400);

    if (!successStatus.contains(status)) {
      return promise.future();
    }

    HttpServerRequest request = context.request();
    User user = context.get(USER);
    JsonObject requestBody = context.body().asJsonObject();
    String userId = user.getUserId();

    JsonObject auditLog = new JsonObject();
    auditLog.put(BODY, requestBody);
    auditLog.put(API, request.uri());
    auditLog.put(METHOD, request.method().toString());
    auditLog.put(USER_ID, userId);

    auditingService.executeWriteQuery(
        auditLog,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.info("{}; {}", SUCC_AUDIT_UPDATE, handler.result());
            promise.complete();
          } else {
            LOGGER.error("{}; {}", ERR_AUDIT_UPDATE, handler.cause().getLocalizedMessage());
            promise.complete();
          }
        });

    return promise.future();
  }

  @Override
  public void stop() {
    LOGGER.info("Stopping the API server");
  }
}
