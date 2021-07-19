package iudx.aaa.server.admin;

import static iudx.aaa.server.admin.Constants.COMPOSE_FAILURE;
import static iudx.aaa.server.admin.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_DOMAIN_EXISTS;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.NIL_UUID;
import static iudx.aaa.server.admin.Constants.SQL_CHECK_ADMIN_OF_SERVER;
import static iudx.aaa.server.admin.Constants.SQL_CREATE_ORG_IF_NOT_EXIST;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_CREATED_ORG;
import static iudx.aaa.server.admin.Constants.URN_ALREADY_EXISTS;
import static iudx.aaa.server.admin.Constants.URN_INVALID_INPUT;
import static iudx.aaa.server.admin.Constants.URN_INVALID_ROLE;
import static iudx.aaa.server.admin.Constants.URN_MISSING_INFO;
import static iudx.aaa.server.admin.Constants.URN_SUCCESS;

import com.google.common.net.InternetDomainName;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreateOrgRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.registration.KcAdmin;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Admin Service Implementation.
 * <h1>Admin Service Implementation</h1>
 * <p>
 * The Admin Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.admin.AdminService}.
 * </p>
 * 
 */
public class AdminServiceImpl implements AdminService {

  private static final Logger LOGGER = LogManager.getLogger(AdminServiceImpl.class);
  private static String AUTH_SERVER_URL;
  private PgPool pool;
  private KcAdmin kc;

  public AdminServiceImpl(PgPool pool, KcAdmin kc, JsonObject options) {
    this.pool = pool;
    this.kc = kc;
    AUTH_SERVER_URL = options.getString(CONFIG_AUTH_URL);
  }

  @Override
  public AdminService getProviderRegistrations(String filter, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AdminService updateProviderRegistrationStatus(List<ProviderUpdateRequest> request,
      Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AdminService createOrganization(CreateOrgRequest request, User user,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    String name = request.getName();
    String url = request.getUrl().toLowerCase();
    String domain;

    Future<Boolean> checkAdmin = checkAdminServer(user, AUTH_SERVER_URL).compose(res -> {
      if (res) {
        return Future.succeededFuture(true);
      }
      Response r = new ResponseBuilder().status(401).type(URN_INVALID_ROLE)
          .title(ERR_TITLE_NOT_AUTH_ADMIN).detail(ERR_DETAIL_NOT_AUTH_ADMIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return Future.failedFuture(COMPOSE_FAILURE);
    });

    try {
      /* Validate and get proper domain */
      InternetDomainName parsedDomain = InternetDomainName.from(url).topDomainUnderRegistrySuffix();
      domain = parsedDomain.toString();
    } catch (IllegalArgumentException | IllegalStateException e) {
      Response r = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
          .title(ERR_TITLE_INVALID_DOMAIN).detail(ERR_DETAIL_INVALID_DOMAIN).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      return this;
    }

    Future<RowSet<Row>> fut = checkAdmin.compose(success -> pool.withConnection(
        conn -> conn.preparedQuery(SQL_CREATE_ORG_IF_NOT_EXIST).execute(Tuple.of(name, domain))));

    fut.onSuccess(rows -> {
      if (rows.rowCount() == 0) {
        Response r = new ResponseBuilder().status(409).type(URN_ALREADY_EXISTS)
            .title(ERR_TITLE_DOMAIN_EXISTS).detail(ERR_DETAIL_DOMAIN_EXISTS).build();
        handler.handle(Future.succeededFuture(r.toJson()));
        return;
      }

      String id = rows.iterator().next().getUUID("id").toString();
      JsonObject resp = new JsonObject();
      resp.put("id", id).put("name", name).put("url", domain);

      Response r = new ResponseBuilder().status(201).type(URN_SUCCESS).title(SUCC_TITLE_CREATED_ORG)
          .objectResults(resp).build();
      handler.handle(Future.succeededFuture(r.toJson()));
    }).onFailure(e -> {
      if (e.getMessage().equals(COMPOSE_FAILURE)) {
        return; // do nothing
      }
      LOGGER.error(e.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });

    return this;
  }

  /**
   * Check if a user is an admin for a particular server.
   * 
   * @param user The user object
   * @param serverUrl The URL/domain of the particuar server
   * @return Future of Boolean type
   */
  private Future<Boolean> checkAdminServer(User user, String serverUrl) {
    Promise<Boolean> p = Promise.promise();

    if (!user.getRoles().contains(Roles.ADMIN)) {
      p.complete(false);
      return p.future();
    }

    pool.withConnection(conn -> conn.preparedQuery(SQL_CHECK_ADMIN_OF_SERVER)
        .execute(Tuple.of(user.getUserId(), serverUrl)).map(row -> row.size()))
        .onSuccess(size -> p.complete(size == 0 ? false : true))
        .onFailure(error -> p.fail(error.getMessage()));

    return p.future();
  }
}
