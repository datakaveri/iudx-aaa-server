package iudx.aaa.server.auditing;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuditingVerticle extends AbstractVerticle {

  private static final String AUDITING_SERVICE_ADDRESS = "iudx.aaa.auditing.service";
  private static final Logger LOGGER = LogManager.getLogger(AuditingVerticle.class);
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private AuditingService auditing;

  @Override
  public void start() throws Exception {

    databaseIP = config().getString("auditingDatabaseIP");
    databasePort = config().getInteger("auditingDatabasePort");
    databaseName = config().getString("auditingDatabaseName");
    databaseUserName = config().getString("auditingDatabaseUserName");
    databasePassword = config().getString("auditingDatabasePassword");
    poolSize = config().getInteger("auditingPoolSize");

    JsonObject propObj = new JsonObject();
    propObj.put("auditingDatabaseIP", databaseIP);
    propObj.put("auditingDatabasePort", databasePort);
    propObj.put("auditingDatabaseName", databaseName);
    propObj.put("auditingDatabaseUserName", databaseUserName);
    propObj.put("auditingDatabasePassword", databasePassword);
    propObj.put("auditingPoolSize", poolSize);

    binder = new ServiceBinder(vertx);
    auditing = new AuditingServiceImpl(propObj, vertx);
    consumer =
        binder.setAddress(AUDITING_SERVICE_ADDRESS).register(AuditingService.class, auditing);
    LOGGER.info("Auditing Verticle Started");
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
