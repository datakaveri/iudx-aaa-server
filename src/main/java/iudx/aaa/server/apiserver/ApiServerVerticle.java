package iudx.aaa.server.apiserver;

import static iudx.aaa.server.apiserver.util.Constants.API_ADMIN_ORG_REGISTER;
import static iudx.aaa.server.apiserver.util.Constants.API_ADMIN_PROVIDERS_REGISTRATIONS;
import static iudx.aaa.server.apiserver.util.Constants.API_ADMIN_PROVIDERS_UPDATE;
import static iudx.aaa.server.apiserver.util.Constants.API_ALL_ORG;
import static iudx.aaa.server.apiserver.util.Constants.API_AUDIT_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.API_CERT_DETAILS;
import static iudx.aaa.server.apiserver.util.Constants.API_COI_REGISTRATION;
import static iudx.aaa.server.apiserver.util.Constants.API_INTROSPECT_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.API_POLICY;
import static iudx.aaa.server.apiserver.util.Constants.API_PROVIDER_REGISTRATION;
import static iudx.aaa.server.apiserver.util.Constants.API_REVOKE_ALL_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.API_REVOKE_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.API_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ACCEPT;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ALLOW_ORIGIN;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_CONTENT_LENGTH;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_CONTENT_TYPE;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_HOST;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ORIGIN;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_REFERER;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.MIME_APPLICATION_JSON;
import static iudx.aaa.server.apiserver.util.Constants.MIME_TEXT_HTML;
import static iudx.aaa.server.apiserver.util.Constants.ROUTE_DOC;
import static iudx.aaa.server.apiserver.util.Constants.ROUTE_STATIC_SPEC;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AbstractVerticle;
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
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import iudx.aaa.server.apiserver.dto.DeletePolicyRequestDTO;
import iudx.aaa.server.apiserver.dto.OrganizationRegistrationDTO;
import iudx.aaa.server.apiserver.dto.RegistrationRequestDTO;
import iudx.aaa.server.apiserver.dto.RevokeAllRequestDTO;
import iudx.aaa.server.apiserver.dto.RevokeRequestDTO;
import iudx.aaa.server.apiserver.dto.TokenRequestDTO;
import iudx.aaa.server.apiserver.dto.UserAccessRequestDTO;
import iudx.aaa.server.apiserver.handlers.CertificateHandler;
import iudx.aaa.server.apiserver.service.AccessAPIService;
import iudx.aaa.server.apiserver.service.OrganizationAPIService;
import iudx.aaa.server.apiserver.service.RegistrationAPIService;
import iudx.aaa.server.apiserver.service.TokenAPIService;
import iudx.aaa.server.apiserver.validation.FailureHandler;
import iudx.aaa.server.apiserver.validation.RequestType;
import iudx.aaa.server.apiserver.validation.ValidationHandlerFactory;
import iudx.aaa.server.certificate.CertificateService;
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
  private static final String CERTIFICATE_SERVICE_ADDRESS = "iudx.aaa.certificate.service";
  private static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private static final String TWOFACTOR_SERVICE_ADDRESS = "iudx.aaa.twofactor.service";

  private TIPService tipService;
  private CertificateService certificateService;
  private PolicyService policyService;
  private RegistrationService registrationService;
  private TokenService tokenService;
  private TwoFactorService twoFactorService;
  private TokenAPIService tokenAPISerivce;
  private RegistrationAPIService registrationAPIService;
  private OrganizationAPIService organizationAPIService;
  private AccessAPIService accessAPIService;

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

    ValidationHandlerFactory validations = new ValidationHandlerFactory();
    FailureHandler failureHandler = new FailureHandler();

    /* Define the APIs, methods, endpoints and associated methods. */

    router = Router.router(vertx);
    router.route().handler(
        CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    router.route().handler(BodyHandler.create());

    router.get(API_CERT_DETAILS).handler(this::certificateInfo);

    router.post(API_TOKEN).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.TOKEN_REQUEST))
        .handler(this::getToken)
        .failureHandler(failureHandler);

    router.post(API_INTROSPECT_TOKEN).handler(CertificateHandler.create())
        .handler(this::introspectToken);

    router.post(API_REVOKE_TOKEN).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.REVOKE_REQUEST))
        .handler(this::revokeToken).failureHandler(failureHandler);

    router.post(API_REVOKE_ALL_TOKEN).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.REVOKE_ALL_REQUEST))
        .handler(this::revokeAllToken).failureHandler(failureHandler);

    router.post(API_AUDIT_TOKEN).handler(this::auditToken);

    router.post(API_PROVIDER_REGISTRATION).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.PROVIDER_REGISTRATION))
        .handler(this::providerRegistration).failureHandler(failureHandler);

    router.post(API_COI_REGISTRATION).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.REGISTRATION))
        .handler(this::registration).failureHandler(failureHandler);

    router.post(API_ADMIN_ORG_REGISTER).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.ORGNIZATION_REGISTRATION))
        .handler(this::registerOrganization).failureHandler(failureHandler);

    router.get(API_ALL_ORG).handler(this::getAllRegisteredOrganizations);

    router.get(API_ADMIN_PROVIDERS_REGISTRATIONS).handler(this::getAllProviderRegistrations);

    router.get(API_ADMIN_PROVIDERS_UPDATE).handler(this::updateProviderRegistration);

    router.get(API_POLICY).handler(this::getAccessPolicies)
          .failureHandler(failureHandler);

    router.post(API_POLICY).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.USER_ACCESS))
        .handler(this::provideUserAccess2Resource).failureHandler(failureHandler);
    
    router.delete(API_POLICY).consumes(MIME_APPLICATION_JSON)
        .handler(validations.getRequestValidator(RequestType.DELETE_ACCESS_POLICY))
        .handler(this::deletePolicy)
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
    certificateService = CertificateService.createProxy(vertx, CERTIFICATE_SERVICE_ADDRESS);
    policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    tokenService = TokenService.createProxy(vertx, TOKEN_SERVICE_ADDRESS);
    twoFactorService = TwoFactorService.createProxy(vertx, TWOFACTOR_SERVICE_ADDRESS);

    tokenAPISerivce = new TokenAPIService(tokenService);
    registrationAPIService = new RegistrationAPIService(registrationService);
    organizationAPIService = new OrganizationAPIService(registrationService);
    accessAPIService = new AccessAPIService(policyService);
  }

  private void certificateInfo(RoutingContext context) {

  }

  private void getToken(RoutingContext context) {
    JsonObject tokenRequestJson = context.getBodyAsJson();
    TokenRequestDTO tokenRequestDTO = tokenRequestJson.mapTo(TokenRequestDTO.class);
    tokenAPISerivce.getToken(tokenRequestDTO).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void introspectToken(RoutingContext context) {

  }

  private void revokeToken(RoutingContext context) {
    JsonObject revokeTokenJson = context.getBodyAsJson();
    RevokeRequestDTO revokeTokenDTO = revokeTokenJson.mapTo(RevokeRequestDTO.class);
    tokenAPISerivce.revoke(revokeTokenDTO).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void revokeAllToken(RoutingContext context) {
    JsonObject revokeAllJson = context.getBodyAsJson();
    RevokeAllRequestDTO revokeAllDTO = revokeAllJson.mapTo(RevokeAllRequestDTO.class);
    tokenAPISerivce.revokeAll(revokeAllDTO).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void auditToken(RoutingContext context) {

  }

  private void providerRegistration(RoutingContext context) {
    JsonObject providerRegistrationJson = context.getBodyAsJson();
    RegistrationRequestDTO registration =
        providerRegistrationJson.mapTo(RegistrationRequestDTO.class);
    registrationAPIService.providerRegistration(registration).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void registration(RoutingContext context) {
    JsonObject registrationJson = context.getBodyAsJson();
    RegistrationRequestDTO registration = registrationJson.mapTo(RegistrationRequestDTO.class);
    registrationAPIService.register(registration).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void registerOrganization(RoutingContext context) {
    JsonObject organizationRegJson = context.getBodyAsJson();
    OrganizationRegistrationDTO registration =
        organizationRegJson.mapTo(OrganizationRegistrationDTO.class);
    organizationAPIService.registerOrganization(registration).onComplete(handler -> {
      if (handler.succeeded()) {
        context.response().putHeader("content-type", "application/json").setStatusCode(200)
            .end(handler.result().toString());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void getAllRegisteredOrganizations(RoutingContext context) {
    organizationAPIService.getAllRegisteredOrganizations().onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void getAllProviderRegistrations(RoutingContext context) {
    String filter = context.queryParam("filter").get(0);
    registrationAPIService.getProviderRegistration(filter).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void updateProviderRegistration(RoutingContext context) {
    String userId = context.queryParam("user_id").get(0);
    String status = context.queryParam("status").get(0);
    registrationAPIService.updateProviderRegistration(userId, status).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void getAccessPolicies(RoutingContext context) {
    HttpServerRequest request = context.request();
    String providerEmail = request.getHeader("provider-email");
    if (providerEmail==null || providerEmail.isEmpty()) {
      ValidationException ex=new ValidationException("mandatory provider-email required in header");
      ex.setParameterName("provider-email");
      context.fail(ex);
    }
    accessAPIService.getAllAccessPolicies(providerEmail).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }

  private void provideUserAccess2Resource(RoutingContext context) {
    HttpServerRequest request = context.request();
    String providerEmail = request.getHeader("provider-email");
    if (providerEmail==null || providerEmail.isEmpty()) {
      ValidationException ex=new ValidationException("mandatory provider-email required in header");
      ex.setParameterName("provider-email");
      context.fail(ex);
    }
    JsonArray requestJson = context.getBodyAsJsonArray();
    List<UserAccessRequestDTO> userAccessDTO = listOf(requestJson);
    accessAPIService.provideUserAccess2User(providerEmail, userAccessDTO).onComplete(handler -> {
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });

  }
  
  private void deletePolicy(RoutingContext context) {
    HttpServerRequest request = context.request();
    String providerEmail = request.getHeader("provider-email");
    if (providerEmail==null || providerEmail.isEmpty()) {
      ValidationException ex=new ValidationException("mandatory provider-email required in header");
      ex.setParameterName("provider-email");
      context.fail(ex);
    }
    JsonArray requestJson = context.getBodyAsJsonArray();
    List<DeletePolicyRequestDTO> deletePolicyDTO=listOf(requestJson);
    accessAPIService.deleteAccessPolicy(providerEmail, deletePolicyDTO).onComplete(handler->{
      if (handler.succeeded()) {
        successResponse(context.response(), 200, handler.result());
      } else {
        context.response().putHeader("content-type", "application/json").setStatusCode(500)
            .end(handler.cause().getMessage());
      }
    });
  }



  private void successResponse(HttpServerResponse response, int statusCode, JsonObject json) {
    response.putHeader("content-type", "application/json").setStatusCode(statusCode)
        .end(json.toString());
  }
  
  public static <T> List<T> listOf(JsonArray arr) {
    if (arr == null) {
      return null;
    } else {
      return (List<T>) arr.getList();
    }
  }

}
