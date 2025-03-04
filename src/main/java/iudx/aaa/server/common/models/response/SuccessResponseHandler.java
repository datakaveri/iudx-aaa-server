package iudx.aaa.server.common.models.response;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static iudx.aaa.server.common.models.response.HttpStatusCode.SUCCESS;
import static iudx.aaa.server.common.models.response.ResponseUrn.SUCCESS_URN;

public class SuccessResponseHandler {
    /**
     * handle HTTP response.
     *
     * @param response HttpServerResponse object
     * @param statusCode Http status code for response
     * @param result String of response
     */
    public static void handleSuccessResponse(HttpServerResponse response, int statusCode, JsonObject result) {
        DbResponseMessageBuilder responseMsg = new DbResponseMessageBuilder();
        responseMsg.statusSuccess();
        responseMsg.addResult(result);
        response.putHeader("content-type", "application/json").setStatusCode(statusCode).end(responseMsg.getResponse().encodePrettily());
    }
    public static void handleSuccessResponse(HttpServerResponse response, int statusCode, JsonArray results) {
        DbResponseMessageBuilder responseMsg = new DbResponseMessageBuilder();
        responseMsg.statusSuccess();
        responseMsg.addResult(results);
        response.putHeader("content-type", "application/json").setStatusCode(statusCode).end(responseMsg.getResponse().encodePrettily());
    }

    /**
     * DbResponseMessageBuilder} Message builder for search APIs.
     */
    private static class DbResponseMessageBuilder {
        private JsonObject response = new JsonObject();
        private JsonArray results = new JsonArray();

        DbResponseMessageBuilder() {
        }

        DbResponseMessageBuilder statusSuccess() {
            response.put("type", SUCCESS_URN.getUrn());
            response.put("title", SUCCESS.getDescription());
            return this;
        }

        DbResponseMessageBuilder setTotalHits(int hits) {
            response.put("totalHits", hits);
            return this;
        }

        /** Overloaded for source only request. */
        DbResponseMessageBuilder addResult(JsonObject obj) {
            response.put("results", results.add(obj));
            return this;
        }
        DbResponseMessageBuilder addResult(JsonArray array) {
            response.put("results", array);
            return this;
        }

        JsonObject getResponse() {
            return response;
        }
    }
}
