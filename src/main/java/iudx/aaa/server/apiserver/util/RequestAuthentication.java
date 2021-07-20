package iudx.aaa.server.apiserver.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User.UserBuilder;

import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.token.Constants.INVALID_CLIENT_ID_SEC;
import static iudx.aaa.server.token.Constants.LOG_DB_ERROR;
import static iudx.aaa.server.token.Constants.LOG_UNAUTHORIZED;
import static iudx.aaa.server.token.Constants.LOG_USER_SECRET;
import static iudx.aaa.server.token.Constants.URN_INVALID_INPUT;

/**
 * Handles Authentication of token, clientId, creation of User Objects.
 *
 */
public class RequestAuthentication implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(RequestAuthentication.class);
  private Vertx vertx;
  private PgPool pgPool;
  private JsonObject keycloakOptions;
  private OAuth2Auth keycloak;

  public RequestAuthentication(Vertx vertx, PgPool pgPool, JsonObject keycloakOptions) {
    this.vertx = vertx;
    this.pgPool = pgPool;
    this.keycloakOptions = keycloakOptions;
    keyCloackAuth();
  }

  @Override
  public void handle(RoutingContext routingContext) {

    MultiMap headers = routingContext.request().headers();
    String token;
    String authorization = headers.get(HttpHeaders.AUTHORIZATION);

    if (authorization != null && !authorization.isBlank()) {
      String[] contents = authorization.split(" ");
      if (contents.length != 2 || !contents[0].equals("Bearer")) {
        token = null;
      } else {
        token = contents[1];
      }
    } else {
      token = null;
    }

    iudx.aaa.server.apiserver.User.UserBuilder user = new UserBuilder();

    /* Handles OIDC Token Flow */
    if (token != null && !token.isBlank()) {
      JsonObject credentials = new JsonObject().put("access_token", token);
      keycloak.authenticate(credentials).onFailure(authHandler -> {
        Response rs = new ResponseBuilder().status(401).type(URN_INVALID_AUTH_TOKEN)
            .title(TOKEN_FAILED).detail(authHandler.getLocalizedMessage()).build();
        routingContext.fail(new Throwable(rs.toJsonString()));
        routingContext.end();

      }).compose(mapper -> {
        LOGGER.info("Info: JWT authenticated");
        User cred = User.create(new JsonObject().put("access_token", token));
        return keycloak.userInfo(cred);
        /*
         * Add extra onFailure as userinfo may not respect leeway. Token may pass authentication,
         * but may fail userinfo auth
         */
      }).onFailure(authHandler -> {
        Response rs = new ResponseBuilder().status(401).type(URN_INVALID_AUTH_TOKEN)
            .title(TOKEN_FAILED).detail(authHandler.getLocalizedMessage()).build();
        routingContext.fail(new Throwable(rs.toJsonString()));
        routingContext.end();

      }).compose(mapper -> {
        LOGGER.debug("Info: UserInfo fetched");
        String kId = mapper.getString(SUB);
        user.keycloakId(kId);

        String[] name = mapper.getString(NAME, " ").split(" ");
        if (name.length == 2) {
          user.name(name[0], name[1]);
        } else {
          user.name(String.join(" ", name).strip(), null);
        }

        return pgSelectUser(SQL_GET_USER_ROLES, kId);

      }).onComplete(kcHandler -> {
        if (kcHandler.succeeded()) {
          JsonObject result = kcHandler.result();

          if (!result.isEmpty()) {
            user.userId(result.getString(ID));
            user.roles(processRoles(result.getJsonArray(ROLES)));
          }

          routingContext.put(CLIENT_ID, result.getString("client_id"));
          routingContext.put(USER, user.build()).next();
        } else if (kcHandler.failed()) {
          LOGGER.error("Fail: Request validation and authentication; " + kcHandler.cause());
          Response rs = new ResponseBuilder().status(500).title(INTERNAL_SVR_ERR)
              .detail(kcHandler.cause().getLocalizedMessage()).build();
          routingContext.fail(new Throwable(rs.toJsonString()));
        }
      });

      /* Handles ClientId Flow */
    } else if (headers.contains(CLIENT_ID) && headers.contains(CLIENT_SECRET)) {
      String clientId = headers.get(CLIENT_ID);
      String clientSecret = headers.get(CLIENT_SECRET);

      if (clientId != null && !clientId.isBlank()) {

        pgSelectUser(SQL_GET_KID_ROLES, clientId).onComplete(dbHandler -> {
          if (dbHandler.failed()) {
            LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
          } else if (dbHandler.succeeded()) {
            if (dbHandler.result().isEmpty()) {
              Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
                  .title(TOKEN_FAILED).detail(TOKEN_FAILED).build();
              routingContext.fail(new Throwable(rs.toJsonString()));
              routingContext.end();
            }
          }

          JsonObject result = dbHandler.result();
          String dbClientSecret = result.getString("client_secret");

          /* Validating clientSecret hash */
          boolean valid = false;
          try {
            valid = OpenBSDBCrypt.checkPassword(dbClientSecret,
                clientSecret.getBytes(StandardCharsets.UTF_8));
          } catch (Exception e) {
            LOGGER.error(LOG_USER_SECRET + e.getLocalizedMessage());
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
            routingContext.fail(new Throwable(resp.toJson().toString()));
            return;
          }

          if (valid == Boolean.FALSE) {
            LOGGER.error(LOG_UNAUTHORIZED + INVALID_CLIENT_ID_SEC);
            Response resp = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
                .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
            routingContext.fail(new Throwable(resp.toJson().toString()));
            return;
          }

          user.keycloakId(result.getString(KID)).userId(result.getString(ID))
              .roles(processRoles(result.getJsonArray(ROLES)));

          routingContext.put(CLIENT_ID, clientId);
          routingContext.put(USER, user.build()).next();
        });
      }
    } else {
      LOGGER.error("Fail: {}; {}", MISSING_TOKEN_CLIENT, "null clientId/token");
      Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
          .title(MISSING_TOKEN_CLIENT).detail(MISSING_TOKEN_CLIENT).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
    }
  }

  /**
   * Creates KeyCloack provider using configurations.
   */
  public void keyCloackAuth() {
    String site = keycloakOptions.getString(SITE);
    String realm = site.substring(site.lastIndexOf('/') + 1);

    /* Options for OAuth2, KeyCloack. */
    OAuth2Options options = new OAuth2Options().setFlow(OAuth2FlowType.CLIENT)
        .setClientID(keycloakOptions.getString(CLIENT_ID))
        .setClientSecret(keycloakOptions.getString(CLIENT_SECRET)).setTenant(realm).setSite(site)
        .setJWTOptions(new JWTOptions().setLeeway(keycloakOptions.getInteger(JWT_LEEWAY)));

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

  /**
   * Handles database queries.
   * 
   * @param sql query
   * @param if of the element
   * @return Future promise which is JsonObject
   */
  public Future<JsonObject> pgSelectUser(String query, String id) {

    Promise<JsonObject> promise = Promise.promise();
    pgPool.withConnection(connection -> connection.preparedQuery(query).execute(Tuple.of(id))
        .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : new JsonObject())
        .onComplete(handler -> {
          if (handler.succeeded()) {
            JsonObject details = handler.result();
            promise.complete(details);
          } else if (handler.failed()) {
            promise.fail(handler.cause());
          }
        }));
    return promise.future();
  }

  /**
   * Creates Roles enum.
   * 
   * @param role
   * @return List having Roles
   */
  public List<Roles> processRoles(JsonArray role) {
    List<Roles> roles = role.stream().filter(a -> Roles.exists(a.toString()))
        .map(a -> Roles.valueOf(a.toString())).collect(Collectors.toList());

    return roles;
  }
}
