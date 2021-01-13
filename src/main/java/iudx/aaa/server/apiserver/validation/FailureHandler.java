package iudx.aaa.server.apiserver.validation;


import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;

public class FailureHandler implements Handler<RoutingContext>{

  private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);
  @Override
  public void handle(RoutingContext context) {
    Throwable failure = context.failure();
    LOGGER.error("error :"+failure);
    if (failure instanceof ValidationException) { 
      String failedParameter=((ValidationException) failure).parameterName();
      LOGGER.error("error :" +failure.getMessage()+" param : "+failedParameter);
      String validationErrorMessage = "<<"+failedParameter+">> "+failure.getMessage();
      context.response()
        .putHeader("content-type", "application/json")
        .setStatusCode(HttpStatus.SC_BAD_REQUEST)
        .end(validationFailureReponse(validationErrorMessage).toString());
    }
    context.next();
    return;
  }
  
  private JsonObject validationFailureReponse(String message) {
    return new JsonObject()
        .put("type", HttpStatus.SC_BAD_REQUEST)
        .put("title", "Bad Request")
        .put("details", message);
  }

}
