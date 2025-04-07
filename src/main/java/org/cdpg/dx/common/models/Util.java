package org.cdpg.dx.common.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.HelperUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Util {
  private static final Logger LOG = LoggerFactory.getLogger(Util.class);

  public static Function<String, URI> toUriFunction =
      (value) -> {
        URI uri = null;
        try {
          uri = new URI(value);
        } catch (URISyntaxException e) {
          JsonArray stackTrace = HelperUtils.convertStackTrace(e);
          LOG.error("Stack trace : {}", stackTrace.encode());
        }
        return uri;
      };

  public static <T> List<T> toList(JsonArray arr) {
    if (arr == null) {
      return null;
    } else {
      return (List<T>) arr.getList();
    }
  }

  public static String errorResponse(HttpStatusCode code) {
    return new JsonObject()
        .put("type", code.getUrn())
        .put("title", code.getDescription())
        .put("detail", code.getDescription())
        .toString();
  }
}
