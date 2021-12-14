package iudx.aaa.server.configuration;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.deploy.ConfigResolve;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;

public class Configuration {

  private static FileSystem fileSystem;
  private static final Logger LOGGER = LogManager.getLogger(Configuration.class);
  private static final String CONFIG_PATH = "./configs/config-test.json";

  /**
   * This is to read the config.json file from fileSystem to load configuration.
   * 
   * @param moduleIndex
   * @param vertx
   * @return module JsonObject
   */
  public JsonObject configLoader(int moduleIndex, Vertx vertx) {

    fileSystem = vertx.fileSystem();
    JsonObject moduleConf = null;

    /* configuration setup */
    File file = new File(CONFIG_PATH);

    if (file.exists()) {
      Buffer buff = fileSystem.readFileBlocking(CONFIG_PATH);
      JsonObject config = buff.toJsonObject();
      try {
        ConfigResolve.resolve(config);
      }
      catch(IllegalStateException e)
      {
        LOGGER.fatal("Invalid option passed in config" + e.getMessage());
        return moduleConf;
      }
      JsonArray allModulesConf = config.getJsonArray("modules");
      moduleConf = allModulesConf.getJsonObject(moduleIndex);

    } else {
      LOGGER.fatal("Couldn't read configuration file; Path: " + CONFIG_PATH);
    }

    return moduleConf;
  }

}
