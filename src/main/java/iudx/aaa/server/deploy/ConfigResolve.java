package iudx.aaa.server.deploy;

import io.vertx.core.json.JsonObject;
import java.util.Iterator;

/**
 * Modify JSON config for different verticles by merging config options required by a verticle into
 * the verticle's config. This prevents repeating config options across all verticles.
 */
public class ConfigResolve {

  /**
   * Resolve config by adding required config options into each verticle's/module's config JSON.
   *
   * @param config the JSON config obtained from the filesystem
   * @return JSON config with resolved options
   * @throws IllegalStateException in case a required option is not present in the <em>options</em>
   *     JSON object in the config
   */
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
