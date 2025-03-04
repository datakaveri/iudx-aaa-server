package iudx.aaa.server.common.models.response;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static iudx.aaa.server.common.models.response.ResponseUrn.BACKING_SERVICE_FORMAT_URN;
import static iudx.aaa.server.common.models.response.ResponseUrn.fromCode;
import static iudx.aaa.server.common.models.response.HttpStatusCode.BAD_REQUEST;
import static iudx.aaa.server.common.models.response.ResponseUtil.generateResponse;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;

public class FailureResponseHandler {
    private static final Logger LOGGER = LogManager.getLogger(FailureResponseHandler.class);
    public static void processBackendResponse(HttpServerResponse response, String failureMessage) {
          LOGGER.info("Info : " + failureMessage);
        try {
            JsonObject json = new JsonObject(failureMessage);
            int type = json.getInteger("type");
            HttpStatusCode status = HttpStatusCode.getByValue(type);
            String urnTitle = json.getString("title");
            ResponseUrn urn;
            if (urnTitle != null) {
                urn = fromCode(urnTitle);
            } else {
                urn = fromCode(String.valueOf(type));
            }
            handleResponse(response, status, urn);
        } catch (DecodeException ex) {
            LOGGER.error("ERROR : Expecting Json from backend service [ jsonFormattingException ]");
            handleResponse(response, BAD_REQUEST, BACKING_SERVICE_FORMAT_URN);
        }
    }

    private static void handleResponse(HttpServerResponse response, HttpStatusCode code, ResponseUrn urn) {
        handleResponse(response, code, urn, code.getDescription());
    }

    private static void handleResponse(
            HttpServerResponse response, HttpStatusCode statusCode, ResponseUrn urn, String message) {
        response
                .putHeader("content-type", APPLICATION_JSON)
                .setStatusCode(statusCode.getValue())
                .end(generateResponse(statusCode, urn, message).toString());
    }
}
