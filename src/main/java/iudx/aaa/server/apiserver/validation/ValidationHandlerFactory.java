package iudx.aaa.server.apiserver.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;

public class ValidationHandlerFactory {

  private final Map<String, String> requestSchema = new HashMap<String, String>();

  public ValidationHandlerFactory() {
    for (RequestType requestType : RequestType.values()) {
      requestSchema.put(requestType.name(), loadJson(requestType.getFileName()));
    }
  }

  /**
   * Request body validation factory, returns a RequestValidationHandler for a requestType
   * 
   * @param requestType
   * @return
   */
  public HTTPRequestValidationHandler getRequestValidator(RequestType requestType) {
    HTTPRequestValidationHandler validator = null;
    String jsonSchema = null;
    try {
      jsonSchema = requestSchema.get(requestType.name());
    } catch (Exception ex) {
      return validator;
    }
    validator = HTTPRequestValidationHandler.create().addJsonBodySchema(jsonSchema);
    return validator;
  }


  /**
   * loads a request json schema from resources schema folder(src/main/resources/schema).
   * 
   * @param fileName
   * @return
   */
  private String loadJson(String fileName) {
    String jsonStr = null;
    try (InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream("schema/" + fileName)) {
      jsonStr = CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
    } catch (IOException e) {
      return jsonStr;
    }
    return jsonStr;
  }

}
