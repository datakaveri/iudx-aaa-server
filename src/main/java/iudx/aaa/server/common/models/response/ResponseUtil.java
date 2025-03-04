package iudx.aaa.server.common.models.response;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResponseUtil {
    private static final Logger LOGGER = LogManager.getLogger(ResponseUtil.class);

    public static JsonObject generateResponse(HttpStatusCode statusCode, ResponseUrn urn) {
        return generateResponse(statusCode, urn, statusCode.getDescription());
    }

    public static JsonObject generateResponse(
            HttpStatusCode statusCode, ResponseUrn urn, String message) {
        String type = urn.getUrn();
        LOGGER.info("resp: "+new RestResponse.Builder()
                .withType(type)
                .withTitle(statusCode.getDescription())
                .withMessage(message)
                .build()
                .toJson());
        return new RestResponse.Builder()
                .withType(type)
                .withTitle(statusCode.getDescription())
                .withMessage(message)
                .build()
                .toJson();
    }
}
