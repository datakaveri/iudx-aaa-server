package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.CONTEXT_SEARCH_USER;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_EMAIL;
import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_ROLE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;

public class SearchUserHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(SearchUserHandler.class);

  public SearchUserHandler() {}

  @Override
  public void handle(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    MultiMap headers = request.headers();

    /* If both aren't there, next. If either is there, fail. If both are
     * there, form JSON object with the data. */
    
    if (!(headers.contains(HEADER_EMAIL) || headers.contains(HEADER_ROLE))) {
      routingContext.next();
      return;
    }

    if (headers.contains(HEADER_EMAIL) ^ headers.contains(HEADER_ROLE)) {
      Response rs = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
          .title(ERR_TITLE_SEARCH_USR).detail(ERR_DETAIL_SEARCH_USR).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
      return;
    }

    String email = headers.get(HEADER_EMAIL);
    String role = headers.get(HEADER_ROLE);

    routingContext.put(CONTEXT_SEARCH_USER, new JsonObject().put("email", email).put("role", role));
    routingContext.next();
  }
}
