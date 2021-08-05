package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.ERR_TITLE_BAD_REQUEST;
import static iudx.aaa.server.apiserver.util.Constants.HEADER_CONTENT_TYPE;
import static iudx.aaa.server.apiserver.util.Constants.INVALID_JSON;
import static iudx.aaa.server.apiserver.util.Constants.JSON_TIMEOUT;
import static iudx.aaa.server.apiserver.util.Constants.MIME_APPLICATION_JSON;
import static iudx.aaa.server.apiserver.util.Constants.STATUS;
import static iudx.aaa.server.apiserver.util.Constants.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Constants.URN_MISSING_INFO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.RequestPredicateException;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;

public class FailureHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);

  @Override
  public void handle(RoutingContext context) {
    HttpServerResponse response = context.response();
    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);

    /* If timeout handler is triggered */
    if (context.failure() == null && context.statusCode() == 503) {
      LOGGER.error("Fail: Handling unexpected error: Timeout");
      response.setStatusCode(503).end(JSON_TIMEOUT);
    }

    LOGGER.error("Fail: Handling unexpected error: " + context.failure().getLocalizedMessage());
    Throwable failure = context.failure();

    if (failure instanceof DecodeException) {
      processResponse(response, URN_INVALID_INPUT, INVALID_JSON);
    } else if (failure instanceof IllegalArgumentException) {
      processResponse(response, URN_INVALID_INPUT, failure.getLocalizedMessage());
    } else if (failure instanceof NullPointerException) {
      response.setStatusCode(500).end();
      return;
    } else if (failure instanceof ParameterProcessorException) {
      processResponse(response, failure.getLocalizedMessage());
      
    } else if (failure instanceof BodyProcessorException) {
      processResponse(response, failure.getLocalizedMessage());
      
    } else if (failure instanceof RequestPredicateException) {
      processResponse(response, failure.getLocalizedMessage());
      
    } else {
      processResponse(response, failure);
      return;
    }
  }

  private Future<Void> processResponse(HttpServerResponse response, Throwable failure) {
    JsonObject msg = new JsonObject(failure.getLocalizedMessage());
    int status = msg.getInteger(STATUS, 400);
    msg.remove(STATUS);
    return response.setStatusCode(status).end(msg.toString());
  }

  private Future<Void> processResponse(HttpServerResponse response, String type, String title) {
    Response rs = new ResponseBuilder().type(type).title(title).detail(title).build();
    return response.setStatusCode(500).end(rs.toJson().toString());
  }

  /* Using this function for 400 Bad Request */
  private Future<Void> processResponse(HttpServerResponse response, String detail) {
    Response rs = new ResponseBuilder().type(URN_INVALID_INPUT).title(ERR_TITLE_BAD_REQUEST)
        .detail(detail).build();
    return response.setStatusCode(400).end(rs.toJson().toString());
  }
}
