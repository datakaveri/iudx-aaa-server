package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.INTERNAL_SVR_ERR;
import static iudx.aaa.server.apiserver.util.Constants.KC_FAMILY_NAME;
import static iudx.aaa.server.apiserver.util.Constants.KC_GIVEN_NAME;
import static iudx.aaa.server.apiserver.util.Constants.OBTAINED_USER_ID;
import static iudx.aaa.server.apiserver.util.Constants.SQL_GET_ADMIN_ROLE;
import static iudx.aaa.server.apiserver.util.Constants.SQL_GET_DELEGATE_ROLE;
import static iudx.aaa.server.apiserver.util.Constants.SQL_GET_PROVIDER_CONSUMER_ROLES;
import static iudx.aaa.server.apiserver.util.Constants.SQL_UNION;
import static iudx.aaa.server.apiserver.util.Constants.USER;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fetch roles and resource servers associated with the roles (if applicable). The roles and RSs are
 * added to the {@link User} object.<br>
 * Note that this class does not implement {@link Handler} of {@link RoutingContext} type since the
 * roles to be searched for needed to be passed as a parameter. The {@link Handler}.handle method
 * does not allow passing parameters.
 *
 */
public class FetchRoles  {

  private PgPool pgPool;
  private JsonObject config;
  private static final Logger LOGGER = LogManager.getLogger(FetchRoles.class);

  public FetchRoles(PgPool pgPool, JsonObject config) {
    this.pgPool = pgPool;
    this.config = config;
  }

  private static Collector<Row, ?, Map<String, JsonArray>> roleToRsCollector =
      Collectors.toMap(row -> row.getString("role"),
          row -> new JsonArray(Arrays.asList(row.getArrayOfStrings("rs_urls"))));

  /**
   * Fetch the requested roles (if the user has them) for the user ID present in the routing
   * context. Also fetch the resource servers for which the roles are applicable. The fetched roles
   * and resource servers are used to make a {@link User} object along with the user ID. This object
   * is then added to the routing context with the key <i>user</i>.
   * 
   * @param ctx the routing context. Should contain the user ID of the user (and name information if
   *        applicable). The {@link User} object with role information is added to the routing
   *        context after fetching the requested roles.
   * @param requestedRoles a set of roles to be obtained (if the user has them)
   */
  public void fetch(RoutingContext ctx, Set<Roles> requestedRoles) {
    UUID userId = UUID.fromString(ctx.get(OBTAINED_USER_ID));
    String firstName = ctx.get(KC_GIVEN_NAME, "");
    String lastName = ctx.get(KC_FAMILY_NAME, "");
    
    User.UserBuilder userBuilder = new UserBuilder();

    userBuilder.userId(userId);
    userBuilder.name(firstName, lastName);
    List<Roles> ownedRoles = new ArrayList<Roles>();

    if (requestedRoles.isEmpty()) {
      ctx.put(USER, userBuilder.build());
      ctx.next();
      return;
    }

    List<String> queries = new ArrayList<String>();
    
    if (requestedRoles.contains(Roles.COS_ADMIN)) {
      if (config.getString("cosAdminUserId").equals(userId.toString())) {
        ownedRoles.add(Roles.COS_ADMIN);
      }
      
      if(requestedRoles.size() == 1) { // only COS admin being checked, skip querying
        userBuilder.roles(ownedRoles); 
        ctx.put(USER, userBuilder.build());
        ctx.next();
        return;
      }
    }

    if (requestedRoles.contains(Roles.CONSUMER) || requestedRoles.contains(Roles.PROVIDER)) {
      queries.add(SQL_GET_PROVIDER_CONSUMER_ROLES);
    }

    if (requestedRoles.contains(Roles.DELEGATE)) {
      queries.add(SQL_GET_DELEGATE_ROLE);
    }

    if (requestedRoles.contains(Roles.ADMIN)) {
      queries.add(SQL_GET_ADMIN_ROLE);
    }
    
    if (queries.isEmpty()) {
      LOGGER.error("Queries array for fetch roles empty for endpoint {}", ctx.request().path());
      throw new IllegalStateException(
          "Query for FetchRoles is empty - are correct roles requested?");
    }

    String finalQuery = String.join(SQL_UNION, queries);

    pgPool.withConnection(conn -> conn.preparedQuery(finalQuery).collecting(roleToRsCollector)
        .execute(Tuple.of(userId)).map(res -> res.value())).compose(roleToRsMap -> {
          
          userBuilder.rolesToRsMapping(roleToRsMap);
          
          ownedRoles.addAll(roleToRsMap.keySet().stream().map(role -> Roles.valueOf(role))
              .collect(Collectors.toList()));
          userBuilder.roles(ownedRoles);

          ctx.put(USER, userBuilder.build());
          
          return Future.succeededFuture();
        }).onSuccess(userObj -> ctx.next()).onFailure(fail -> {
          LOGGER.error("Fail: Fetch roles: {} ", fail.getMessage());

          Response rs = new ResponseBuilder().status(500).title(INTERNAL_SVR_ERR)
              .detail(INTERNAL_SVR_ERR).build();
          ctx.fail(new Throwable(rs.toJsonString()));
        });
  }
}