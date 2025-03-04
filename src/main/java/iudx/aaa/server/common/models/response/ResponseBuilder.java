package iudx.aaa.server.common.models.response;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static iudx.aaa.server.common.models.response.HttpStatusCode.SUCCESS;


public class ResponseBuilder {


    private final JsonObject response;

    /** Initialise the object with Success or Failure. */
    public ResponseBuilder() {
        response = new JsonObject();
    }

    public ResponseBuilder setTypeAndTitle(int statusCode) {

        if (200 == statusCode) {
            response.put("type", ResponseUrn.SUCCESS_URN.getUrn());
            response.put("title", SUCCESS);
        } else if (204 == statusCode) {
            response.put("type", statusCode);
            response.put("title", SUCCESS);
        } else {
            response.put("type", statusCode);
            response.put("title", ResponseUrn.BAD_REQUEST_URN.getUrn());
        }
        return this;
    }

    /** Overloaded methods for Error messages. */
    public ResponseBuilder setMessage(String error) {
        response.put("detail", error);
        return this;
    }

    public ResponseBuilder setCount(int count) {
        response.put("results", new JsonArray().add(new JsonObject().put("total", count)));
        return this;
    }

    public ResponseBuilder setData(JsonArray jsonArray) {
        response.put("results", jsonArray);
        return this;
    }

    public ResponseBuilder setTotalHits(int totalHits) {
        response.put("totalHits", totalHits);
        return this;
    }

    public JsonObject getResponse() {
        return response;
    }
}
