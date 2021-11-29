package iudx.aaa.server.deploy;

import java.util.Iterator;
import io.vertx.core.json.JsonObject;

public class ConfigResolve {

  public static JsonObject resolve(JsonObject config) throws IllegalStateException {

    JsonObject options = config.getJsonObject("options");
    Iterator<Object> iterateModules = config.getJsonArray("modules").iterator();
    while (iterateModules.hasNext()) {
      JsonObject module = (JsonObject) iterateModules.next();
      
      if (!module.containsKey("required")) {
        continue;
      }
      
      Iterator<Object> iterateOptions = module.getJsonArray("required").iterator();
      while (iterateOptions.hasNext()) {
        String option = (String) iterateOptions.next();
        if (!options.containsKey(option)) {
          throw new IllegalStateException("Option not found " + option);
        }
        module.mergeIn(options.getJsonObject(option));
      }
    }

    return config;
  }
}
