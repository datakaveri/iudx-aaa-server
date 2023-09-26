package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.apiserver.util.Urn.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;

/**
 * Handles Keycloak token authentication. Validates that the token is valid and not expired. If
 * validation successful, adds the <i>sub</i> field from the Keycloak token - the Keycloak user ID -
 * and the first and last names of the user to the routing context. The Keycloak user ID is the user
 * ID used in all tables and throughout the DX system.
 */
public class OIDCAuthentication implements AuthenticationHandler {

  private static final Logger LOGGER = LogManager.getLogger(OIDCAuthentication.class);
  private Vertx vertx;
  private JsonObject keycloakOptions;
  private OAuth2Auth keycloak;

  public OIDCAuthentication(Vertx vertx, JsonObject keycloakOptions) {
    this.vertx = vertx;
    this.keycloakOptions = keycloakOptions;
    keyCloackAuth();
  }
  
  @Override
  public void handle(RoutingContext routingContext) {
    
    final HttpServerRequest request = routingContext.request();
    final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
    String tokenPath = request.path();
    String token;

    if (authorization != null && !authorization.isBlank()) {
      String[] contents = authorization.split(" ");
      if (contents.length != 2 || !contents[0].equals(BEARER)) {
        token = null;
      } else {
        token = contents[1];
      }
    } else {
      token = null;
    }

    /* Handles OIDC Token Flow
     * A combination of routingContext.fail and routingContext.end ends the compose
     * chain and prevents all the onFailure blocks from being triggered */
    if (token != null && !token.isBlank()) {
      TokenCredentials credentials = new TokenCredentials().setToken(token);
      keycloak.authenticate(credentials).onFailure(authHandler -> {
        Response rs = new ResponseBuilder().status(401).type(URN_INVALID_AUTH_TOKEN)
            .title(TOKEN_FAILED).detail(authHandler.getLocalizedMessage()).build();
        routingContext.fail(new Throwable(rs.toJsonString()));

      }).compose(mapper -> {
        User cred = User.create(new JsonObject().put("access_token", token));
        return keycloak.userInfo(cred);
        /*
         * Add extra onFailure as userinfo may not respect leeway. Token may pass authentication,
         * but may fail userinfo auth
         */
      }).onFailure(authHandler -> {
        Response rs = new ResponseBuilder().status(401).type(URN_INVALID_AUTH_TOKEN)
            .title(TOKEN_FAILED).detail(authHandler.getLocalizedMessage()).build();
        /*
         * since there are multiple failure blocks in this compose chain, check to see if
         * routingContext has already failed, to avoid an IllegalStateException
         */
        if (!routingContext.failed()) {
          routingContext.fail(new Throwable(rs.toJsonString()));
        }
      }).compose(mapper -> {
        LOGGER.debug("Info: JWT authenticated; UserInfo fetched");
        String kId = mapper.getString(SUB);
        routingContext.put(OBTAINED_USER_ID, kId);

        String firstName = mapper.getString(KC_GIVEN_NAME, " ");
        String lastName = mapper.getString(KC_FAMILY_NAME, " ");
        routingContext.put(KC_GIVEN_NAME, firstName);
        routingContext.put(KC_FAMILY_NAME, lastName);
        
        return Future.succeededFuture();

      }).onComplete(kcHandler -> {
        if (kcHandler.succeeded()) {
          
          routingContext.next();
          
        } else if (kcHandler.failed()) {
          LOGGER.error("Fail: Request validation and authentication; " + kcHandler.cause());
          Response rs = new ResponseBuilder().status(500).title(INTERNAL_SVR_ERR)
              .detail(INTERNAL_SVR_ERR).build();
          /*
           * since there are multiple failure blocks in this compose chain, check to see if
           * routingContext has already failed, to avoid an IllegalStateException
           */
          if (!routingContext.failed()) {
            routingContext.fail(new Throwable(rs.toJsonString()));
          }
        }
      });

      /* Handles ClientId Flow */
    } else {
      if (TOKEN_ROUTE.equals(tokenPath)) {
        routingContext.next();
        return;
      }

      LOGGER.error("Fail: {}; {}", MISSING_TOKEN_CLIENT, "null clientId/token");
      Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
          .title(MISSING_TOKEN_CLIENT).detail(MISSING_TOKEN_CLIENT).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
    }
  }

  /**
   * Creates KeyCloack provider using configurations.
   * keycloakOptions is a JSON object containing the required
   * keys. (It is actually the full config verticle config object)
   */
  public void keyCloackAuth() {
    String site = keycloakOptions.getString(KEYCLOAK_SITE);
    String realm = keycloakOptions.getString(KEYCLOAK_REALM);

    /* Options for OAuth2, KeyCloack. */
    OAuth2Options options = new OAuth2Options()
        .setClientId(keycloakOptions.getString(KEYCLOAK_ADMIN_CLIENT_ID))
        .setClientSecret(keycloakOptions.getString(KEYCLOAK_ADMIN_CLIENT_SECRET))
        .setTenant(realm).setSite(site)
        .setJWTOptions(new JWTOptions().setLeeway(keycloakOptions.getInteger(KEYCLOAK_JWT_LEEWAY)));

    options.getHttpClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);

    /* Discovers the keycloack instance */
    KeycloakAuth.discover(vertx, options, discover -> {
      if (discover.succeeded()) {
        keycloak = discover.result();
      } else {
        LOGGER.error(LOG_FAILED_DISCOVERY + discover.cause());
      }
    });
  }
}
