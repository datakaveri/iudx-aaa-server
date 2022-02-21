package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.DATABASE_IP;
import static iudx.aaa.server.apd.Constants.DATABASE_NAME;
import static iudx.aaa.server.apd.Constants.DATABASE_PASSWORD;
import static iudx.aaa.server.apd.Constants.DATABASE_POOLSIZE;
import static iudx.aaa.server.apd.Constants.DATABASE_PORT;
import static iudx.aaa.server.apd.Constants.DATABASE_SCHEMA;
import static iudx.aaa.server.apd.Constants.DATABASE_USERNAME;
import static iudx.aaa.server.apd.Constants.DB_CONNECT_TIMEOUT;
import static iudx.aaa.server.apd.Constants.REGISTRATION_SERVICE_ADDRESS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.registration.RegistrationService;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Apd Verticle.
 * <h1>Apd Verticle</h1>
 * <p>
 * The Apd Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.apd.ApdService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 * @since 2020-12-15
 */

public class ApdVerticle extends AbstractVerticle {

  /* Database Properties */
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseSchema;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;

  private PgPool pool;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;
  private static final String APD_SERVICE_ADDRESS = "iudx.aaa.apd.service";
  private static JsonObject options;
  private WebClient webClient;
  private WebClientOptions webClientOptions;
  private ApdWebClient apdWebClient;
  private RegistrationService registrationService;

  private ApdService apdService;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private static final Logger LOGGER = LogManager.getLogger(ApdVerticle.class);

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, registers the
   * service with the Event bus against an address, publishes the service with the service discovery
   * interface.
   */
  @Override
  public void start() throws Exception {

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : " + LOGGER.getName() + " : Reading config file");

    databaseIP = config().getString(DATABASE_IP);
    databasePort = Integer.parseInt(config().getString(DATABASE_PORT));
    databaseName = config().getString(DATABASE_NAME);
    databaseSchema = config().getString(DATABASE_SCHEMA);
    databaseUserName = config().getString(DATABASE_USERNAME);
    databasePassword = config().getString(DATABASE_PASSWORD);
    poolSize = Integer.parseInt(config().getString(DATABASE_POOLSIZE));

    /*
     * Pass an `options` JSON object to the serviceImpl with a key:val being the authServerDomain
     */
    options = new JsonObject().put(CONFIG_AUTH_URL, config().getString(CONFIG_AUTH_URL));

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setConnectTimeout(DB_CONNECT_TIMEOUT).setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */
    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Create the APD web client */
    webClientOptions = new WebClientOptions().setSsl(true).setVerifyHost(true).setTrustAll(true)
        .setFollowRedirects(false);
    webClient = WebClient.create(vertx, webClientOptions);
    apdWebClient = new ApdWebClient(webClient);

    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    apdService = new ApdServiceImpl(pool, apdWebClient, registrationService, options);
    binder = new ServiceBinder(vertx);
    consumer = binder.setAddress(APD_SERVICE_ADDRESS).register(ApdService.class, apdService);

    LOGGER.debug("Info : " + LOGGER.getName() + " : Started");
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
