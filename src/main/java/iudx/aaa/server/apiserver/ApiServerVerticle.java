package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.util.Constants.*;

import com.nimbusds.jose.jwk.ECKey;
import iudx.aaa.server.auditing.AuditingService;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import iudx.aaa.server.apiserver.util.FetchRoles;
import iudx.aaa.server.apiserver.util.OIDCAuthentication;
import iudx.aaa.server.apiserver.util.DelegationIdAuthorization;
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

  /** Service addresses */
  private static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private static final String ADMIN_SERVICE_ADDRESS = "iudx.aaa.admin.service";
  private static final String AUDITING_SERVICE_ADDRESS = "iudx.aaa.auditing.service";
  private static final String APD_SERVICE_ADDRESS = "iudx.aaa.apd.service";
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
   *
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

      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setConnectTimeout(PG_CONNECTION_TIMEOUT).setProperties(schemaProp)
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

    allowedHeaders.add(HEADER_DELEGATION_ID);
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
    OIDCAuthentication oidcFlow = new OIDCAuthentication(vertx, config());
    FetchRoles fetchRoles = new FetchRoles(pgPool, config());
    ClientAuthentication clientFlow = new ClientAuthentication(pgPool);
    DelegationIdAuthorization delegationAuth = new DelegationIdAuthorization(pgPool);
    FailureHandler failureHandler = new FailureHandler();

    RouterBuilder.create(vertx, "docs/openapi.yaml").onFailure(Throwable::printStackTrace)
            .onSuccess(routerBuilder -> {
              LOGGER.debug("Info: Mouting routes from OpenApi3 spec");

              RouterBuilderOptions factoryOptions = new RouterBuilderOptions()
                      .setMountResponseContentTypeHandler(true);
              //  .setRequireSecurityHandlers(false);
              routerBuilder.setOptions(factoryOptions);
              routerBuilder.securityHandler("authorization", oidcFlow);

              // Post token create
              routerBuilder.operation(CREATE_TOKEN)
                      .handler(clientFlow)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(delegationAuth)
                      .handler(this::createTokenHandler)
                      .failureHandler(failureHandler);

              // Post token introspect
              routerBuilder.operation(TIP_TOKEN)
                      .handler(this::validateTokenHandler)
                      .failureHandler(failureHandler);

              // Post token revoke
              routerBuilder.operation(REVOKE_TOKEN)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::revokeTokenHandler)
                      .failureHandler(failureHandler);

              // Post user profile
              routerBuilder.operation(ADD_ROLES)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::createUserProfileHandler)
                      .failureHandler(failureHandler);

              // Get user profile
              routerBuilder.operation(GET_USER_ROLES)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::listUserProfileHandler)
                      .failureHandler(failureHandler);

              routerBuilder.operation(RESET_CLIENT_CRED)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::resetClientCredHandler)
                      .failureHandler(failureHandler);

              // Get Resource Server Details
              routerBuilder.operation(GET_RESOURCE_SERVERS)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of()))
                      .handler(this::listResourceServersHandler)
                      .failureHandler(failureHandler);

              // Post Create Organization
              routerBuilder.operation(CREATE_RESOURCE_SERVER)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.COS_ADMIN)))
                      .handler(this::adminCreateResourceServerHandler)
                      .failureHandler(failureHandler);

              // Get Provider registrations
              routerBuilder.operation(GET_PVDR_REGISTRATION)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.ADMIN)))
                      .handler(this::adminGetProviderRegHandler)
                      .failureHandler(failureHandler);

              // Update Provider registration status
              routerBuilder.operation(UPDATE_PVDR_REGISTRATION)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.ADMIN)))
                      .handler(this::adminUpdateProviderRegHandler)
                      .failureHandler(failureHandler);

              // Get delegations by provider/consumer/delegate
              routerBuilder.operation(GET_DELEGATIONS)
                      .handler(ctx -> fetchRoles.fetch(ctx,
                          Set.of(Roles.CONSUMER, Roles.PROVIDER, Roles.DELEGATE)))
                      .handler(this::listDelegationsHandler)
                      .failureHandler(failureHandler);

              // Delete delegations by provider/consumer/delegate
              routerBuilder.operation(DELETE_DELEGATIONS)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.PROVIDER, Roles.CONSUMER)))
                      .handler(this::deleteDelegationsHandler)
                      .failureHandler(failureHandler);

              // Create delegations
              routerBuilder.operation(CREATE_DELEGATIONS)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.PROVIDER, Roles.CONSUMER)))
                      .handler(this::createDelegationsHandler)
                      .failureHandler(failureHandler);

              // Get delegate emails
              routerBuilder.operation(GET_DELEGATE_EMAILS)
                      .handler(clientFlow)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.TRUSTEE)))
                      .handler(this::getDelegateEmailsHandler)
                      .failureHandler(failureHandler);

              // Create APD
              routerBuilder.operation(CREATE_APD)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.COS_ADMIN)))
                      .handler(this::createApdHandler)
                      .failureHandler(failureHandler);
              
              // Update APD status
              routerBuilder.operation(UPDATE_APD)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.COS_ADMIN)))
                      .handler(this::updateApdHandler)
                      .failureHandler(failureHandler);

              // List APDs
              routerBuilder.operation(LIST_APD)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::listApdHandler)
                      .failureHandler(failureHandler);

              // Get default client credentials
              routerBuilder.operation(GET_DEFAULT_CLIENT_CREDS)
                      .handler(ctx -> fetchRoles.fetch(ctx, Roles.allRoles))
                      .handler(this::getDefaultClientCredsHandler)
                      .failureHandler(failureHandler);

              // Search User
              routerBuilder.operation(SEARCH_USER)
                      .handler(clientFlow)
                      .handler(ctx -> fetchRoles.fetch(ctx, Set.of(Roles.TRUSTEE)))
                      .handler(this::searchUserHandler)
                      .failureHandler(failureHandler);

              // Get PublicKey
              routerBuilder.operation(GET_CERT)
                      .handler(this::pubCertHandler);
              // Get PublicKey in JWKS format
              routerBuilder.operation(GET_JWKS).handler(this::retrievePublicKey);

              /* TimeoutHandler needs to be added as rootHandler */
              routerBuilder.rootHandler(TimeoutHandler.create(serverTimeout));

              // Router configuration- CORS, methods and headers
              routerBuilder.rootHandler(CorsHandler.create(corsRegex)
                      .allowedHeaders(allowedHeaders)
                      .allowedMethods(allowedMethods));

              routerBuilder.rootHandler(BodyHandler.create());

              router = routerBuilder.createRouter();

              // Static Resource Handler.Get openapiv3 spec
              router.get(ROUTE_STATIC_SPEC)
                      .produces(MIME_APPLICATION_JSON)
                      .handler(routingContext -> {
                        HttpServerResponse response = routingContext.response();
                        response.sendFile("docs/openapi.yaml");
                      });

              // Get redoc
              router.get(ROUTE_DOC).produces(MIME_TEXT_HTML)
                      .handler(routingContext -> {
                        HttpServerResponse response = routingContext.response();
                        response.sendFile("docs/apidoc.html");
                      });

              /* In case API/method not implemented, this last route is triggered */
              router.route().last().handler(routingContext-> {
                HttpServerResponse response = routingContext.response();
                response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
                .setStatusCode(404).end(JSON_NOT_FOUND);
              });

              HttpServerOptions serverOptions = new HttpServerOptions();
              LOGGER.debug("Info: Starting HTTP server");

              /*
               * Setup the HTTP server properties, APIs and port. Default port is 8080. If set through
               * config, then that value is taken
               */
              serverOptions.setSsl(false);
              port = config().getInteger("httpPort") == null ? 8080 : config().getInteger("httpPort");
              LOGGER.info("Info: Starting HTTP server at port " + port);

              serverOptions.setCompressionSupported(true).setCompressionLevel(5);
              server = vertx.createHttpServer(serverOptions);
              server.requestHandler(router).listen(port).onSuccess(success -> {
                LOGGER.debug("Info: Started HTTP server");
              }).onFailure(err -> {
                LOGGER.fatal("Info: Failed to start HTTP server - " + err.getMessage());
              });

              /* Get a handler for the Service Discovery interface. */
              policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
              registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
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
    RequestToken requestTokenDTO = new RequestToken(tokenRequestJson);
    User user = context.get(USER);
    
    DelegationInformation delegationInfo = context.get(DELEGATION_INFO);

    tokenService.createToken(requestTokenDTO, delegationInfo, user, handler -> {
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
    /* Mapping request body to Object */
    JsonObject tokenRequestJson = context.body().asJsonObject();
    RevokeToken revokeTokenDTO = tokenRequestJson.mapTo(RevokeToken.class);

    User user = context.get(USER);

    tokenService.revokeToken(revokeTokenDTO, user, handler -> {
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
    AddRolesRequest request = new AddRolesRequest(jsonRequest);
    User user = context.get(USER);

    registrationService.addRoles(request, user, handler -> {
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

    registrationService.listUser(user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Handles client credential reset.
   *
   * @param context
   */
  private void resetClientCredHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    ResetClientSecretRequest request = new ResetClientSecretRequest(jsonRequest);
    User user = context.get(USER);

    registrationService.resetClientSecret(request, user, handler -> {
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
   * Handles resource server listing.
   *
   * @param context
   */
  private void listResourceServersHandler(RoutingContext context) {
    registrationService.listResourceServer(handler -> {
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
  private void adminCreateResourceServerHandler(RoutingContext context) {
    JsonObject jsonRequest = context.body().asJsonObject();
    CreateRsRequest request = new CreateRsRequest(jsonRequest);
    User user = context.get(USER);

    adminService.createResourceServer(request, user, handler -> {
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
    adminService.getProviderRegistrations(filter, user, handler -> {
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
    adminService.updateProviderRegistrationStatus(request, user, handler -> {
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

    policyService.listDelegation(user, handler -> {
      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   *
   * @param context
   */

  private void deleteDelegationsHandler(RoutingContext context) {

    JsonObject jsonRequest = context.body().asJsonObject();
    JsonArray arr = jsonRequest.getJsonArray(REQUEST);
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(arr);

    User user = context.get(USER);

    policyService.deleteDelegation(request, user, handler -> {
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
   *
   * @param context
   */

  private void getDelegateEmailsHandler(RoutingContext context) {

    User user = context.get(USER);
    
    List<String> userIdList = context.queryParam(QUERY_USERID);
    List<String> roleList = context.queryParam(QUERY_ROLE);
    List<String> rsList = context.queryParam(QUERY_RESOURCE_SERVER);

    String delegatorUserId = userIdList.get(0).toLowerCase();
    Roles delegatedRole = Roles.valueOf(roleList.get(0).toUpperCase());
    String delegatedRsUrl = rsList.get(0).toLowerCase();

    policyService.getDelegateEmails(user, delegatorUserId, delegatedRole, delegatedRsUrl,
        handler -> {
          if (handler.succeeded()) {
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

    policyService.createDelegation(request, user, handler -> {

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

    apdService.createApd(request, user, handler -> {

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

    apdService.listApd(user, handler -> {

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

    apdService.updateApd(request, user, handler -> {

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
   * Get default client credentials.
   * 
   * @param context
   */
  private void getDefaultClientCredsHandler(RoutingContext context) {

    User user = context.get(USER);

    registrationService.getDefaultClientCredentials(user, handler -> {

      if (handler.succeeded()) {
        processResponse(context.response(), handler.result());
      } else {
        processResponse(context.response(), handler.cause().getLocalizedMessage());
      }
    });
  }

  /**
   * Search for user.
   * 
   * @param context
   */
  private void searchUserHandler(RoutingContext context) {
    User user = context.get(USER);
    
    List<String> emailList = context.queryParam(QUERY_EMAIL);
    List<String> userIdList = context.queryParam(QUERY_USERID);
    List<String> roleList = context.queryParam(QUERY_ROLE);
    List<String> rsList = context.queryParam(QUERY_RESOURCE_SERVER);
    
    if(!emailList.isEmpty() && !userIdList.isEmpty()) {
        throw new IllegalArgumentException(ERR_DETAIL_SEARCH_BOTH_PARAMS);
    }

    if(emailList.isEmpty() && userIdList.isEmpty()) {
        throw new IllegalArgumentException(ERR_DETAIL_SEARCH_MISSING_PARAMS);
    }
    
    String searchString;
    if(!emailList.isEmpty()) {
      searchString = emailList.get(0).toLowerCase();
    }else {
      searchString = userIdList.get(0).toLowerCase();
    }
    
    Roles role = Roles.valueOf(roleList.get(0).toUpperCase());
    String resourceServerUrl = rsList.get(0).toLowerCase();
    
    registrationService.searchUser(user, searchString, role, resourceServerUrl, handler -> {

      if (handler.succeeded()) {
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

        String certKeyString = "-----BEGIN CERTIFICATE-----\n"
                + pubKeyCertEncoded
                + "\n-----END CERTIFICATE-----";

        context.response().putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
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
   * @param response
   * @param msg JsonObject
   * @return context response
   */
  private Future<Void> processResponse(HttpServerResponse response, JsonObject msg) {
    int status = msg.getInteger(STATUS, 400);
    msg.remove(STATUS);
    
    /* In case of a timeout, the response may already be sent */
    if(response.ended()) {
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
   * Handles succeeded audit logs. Creates the required {@link JsonObject} from the
   * {@link RoutingContext} and pass on to it {@link AuditingService} for database storage when the
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

    auditingService.executeWriteQuery(auditLog, handler -> {
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
