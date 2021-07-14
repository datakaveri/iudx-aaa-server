package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.*;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User.UserBuilder;

public class RequestAuthentication implements Handler<RoutingContext> {
  
  private static final Logger LOGGER = LogManager.getLogger(RequestAuthentication.class);
  private Vertx vertx;
  private PgPool pgPool;
  private WebClient client;
  private JsonObject keycloakOptions;
  private OAuth2Auth keycloak;
  
  public RequestAuthentication(Vertx vertx, PgPool pgPool, JsonObject keycloakOptions) {
    this.vertx = vertx;
    this.pgPool = pgPool;
    this.keycloakOptions = keycloakOptions;

    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);

    client = WebClient.create(vertx, clientOptions);
    keyCloackAuth();

  }

  /*
   * public static RequestAuthentication create(Vertx vertx, PgPool pgPool) {
   * RequestAuthentication.vertx = vertx; RequestAuthentication.pgPool = pgPool;
   * 
   * WebClientOptions clientOptions = new WebClientOptions() .setSsl(true) .setVerifyHost(false)
   * .setTrustAll(true);
   * 
   * client = WebClient.create(vertx,clientOptions);
   * 
   * return new RequestAuthentication(); }
   */

  @Override
  public void handle(RoutingContext routingContext) {

    HttpServerRequest request = routingContext.request();
    String token = routingContext.request().getHeader(HEADER_TOKEN);
    JsonObject requestBody = routingContext.getBodyAsJson();
    JsonObject credentials = new JsonObject().put("access_token", token);
    iudx.aaa.server.apiserver.User.UserBuilder user = new UserBuilder();

    if (token != null && !token.isBlank()) {
      keycloak.authenticate(credentials).compose(mapper -> {

        LOGGER.debug("JWT------- SUccess: " + mapper.attributes());
        User cred = User.create(new JsonObject().put("access_token", token));
        return keycloak.userInfo(cred);
      }).compose(mapper -> {
        String kId = mapper.getString("sub");
        String[] name = mapper.getString("name").split(" ");
        
        user.keycloakId(kId);
        user.name(name[0], name[1]);
        return pgSelectUser(SQL_GET_USER_ROLES, kId);
      }).onComplete(kcHandler -> {
        if (kcHandler.succeeded()) {
          JsonObject result = kcHandler.result();
          user.userId(result.getString("id"));
          user.roles(Arrays.asList(Roles.values()));
          LOGGER.debug("USER----: " +"" );
          routingContext.next();
        } else {
          LOGGER.debug("USER----: " + kcHandler.cause());
          routingContext.end();
        }
      });
    } else {
      routingContext.end();
    }
  }
  
  Future<JsonObject> httpPostFormAsync(String token) {
    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions(keycloakOptions);

    options.addHeader("Authorization", "Bearer " + token);

    client.request(HttpMethod.GET, options).send(resHandler -> {
      if (resHandler.succeeded()) {
        HttpResponse<Buffer> results = resHandler.result();
        if (results.statusCode() == 200) {
          promise.complete(results.bodyAsJsonObject());
        } else {
          promise.fail(results.bodyAsString());
        }
      } else if (resHandler.failed()) {
        promise.fail(resHandler.cause());
      }
    });

    return null;
  }
  
  public void keyCloackAuth() {
    String site = keycloakOptions.getString("site");
    String realm = site.substring(site.lastIndexOf('/') + 1);
    
    OAuth2Options options = new OAuth2Options()
        .setFlow(OAuth2FlowType.CLIENT)
        .setClientID(keycloakOptions.getString("clientId"))
        .setClientSecret(keycloakOptions.getString("clientSecret"))
        .setTenant(realm)
        .setSite(site)
        .setJWTOptions(new JWTOptions().setLeeway(keycloakOptions.getInteger("jwtLeeway")));
    
    options.getHttpClientOptions().setSsl(true)
    .setVerifyHost(false)
    .setTrustAll(true);

    KeycloakAuth.discover(vertx, options, discover -> {
      if (discover.succeeded()) {
        keycloak = discover.result();
      } else {
        LOGGER.error("JWT ERROR: " + discover.cause());
      }
    });
  } 
  
  Future<JsonObject> pgSelectUser(String query, String userId) {

    Promise<JsonObject> promise = Promise.promise();
    pgPool.withConnection(connection -> connection.preparedQuery(query).execute(Tuple.of(userId))
        .map(rows -> rows.iterator().next().toJson()).onComplete(handler -> {
          if (handler.succeeded()) {
            JsonObject details = handler.result();
            promise.complete(details);
          } else if (handler.failed()) {
            promise.fail(handler.cause());
          }
        }));
    return promise.future();
  }
}
