package iudx.aaa.server.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.registration.KcAdmin;

public class AdminServiceImpl implements AdminService {

  private static final Logger LOGGER = LogManager.getLogger(AdminServiceImpl.class);

  private PgPool pool;
  private KcAdmin kc;

  public AdminServiceImpl(PgPool pool, KcAdmin kc) {
    this.pool = pool;
    this.kc = kc;
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
  public AdminService createOrganization(Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    return null;
  }
}
