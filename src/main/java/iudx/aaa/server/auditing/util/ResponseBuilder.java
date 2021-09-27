package iudx.aaa.server.auditing.util;

import static iudx.aaa.server.auditing.util.Constants.DETAIL;
import static iudx.aaa.server.auditing.util.Constants.ERROR_TYPE;
import static iudx.aaa.server.auditing.util.Constants.FAILED;
import static iudx.aaa.server.auditing.util.Constants.RESULTS;
import static iudx.aaa.server.auditing.util.Constants.SUCCESS;
import static iudx.aaa.server.auditing.util.Constants.TITLE;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResponseBuilder {
  private String actionStatus;
  private JsonObject response;

  /** Initialize the object with Success or Failure. */
  public ResponseBuilder(String actionStatus) {
    this.actionStatus = actionStatus;
    response = new JsonObject();
  }

  public ResponseBuilder setTypeAndTitle(int statusCode) {
    response.put(ERROR_TYPE, statusCode);
    if (SUCCESS.equalsIgnoreCase(actionStatus)) {
      response.put(TITLE, SUCCESS);
    } else if (FAILED.equalsIgnoreCase(actionStatus)) {
      response.put(TITLE, FAILED);
    }
    return this;
  }

  /** Overloaded methods for Error messages. */
  public ResponseBuilder setMessage(String error) {
    response.put(DETAIL, error);
    return this;
  }

  public ResponseBuilder setJsonArray(JsonArray jsonArray) {
    response.put(RESULTS, jsonArray);
    return this;
  }

  public JsonObject getResponse() {
    return response;
  }
}
