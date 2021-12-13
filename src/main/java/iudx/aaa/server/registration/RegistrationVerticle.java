package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.DATABASE_IP;
import static iudx.aaa.server.registration.Constants.DB_CONNECT_TIMEOUT;
import static iudx.aaa.server.registration.Constants.DATABASE_NAME;
import static iudx.aaa.server.registration.Constants.DATABASE_PASSWORD;
import static iudx.aaa.server.registration.Constants.DATABASE_POOLSIZE;
import static iudx.aaa.server.registration.Constants.DATABASE_PORT;
import static iudx.aaa.server.registration.Constants.DATABASE_SCHEMA;
import static iudx.aaa.server.registration.Constants.DATABASE_USERNAME;
import static iudx.aaa.server.registration.Constants.KC_ADMIN_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.KC_ADMIN_CLIENT_SEC;
import static iudx.aaa.server.registration.Constants.KC_ADMIN_POOLSIZE;
import static iudx.aaa.server.registration.Constants.KEYCLOAK_REALM;
import static iudx.aaa.server.registration.Constants.KEYCLOAK_URL;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;

/**
 * The Registration Verticle.
 * <h1>Registration Verticle</h1>
 * <p>
 * The Registration Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.registration.RegistrationService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 * @since 2020-12-15
 */

public class RegistrationVerticle extends AbstractVerticle {

  /* Database Properties */
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseSchema;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;

  private String keycloakUrl;
  private String keycloakRealm;
  private String keycloakAdminClientId;
  private String keycloakAdminClientSecret;
  private int keycloakAdminPoolSize;

  private PgPool pool;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;
  private static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  private RegistrationService registrationService;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private static final Logger LOGGER = LogManager.getLogger(RegistrationVerticle.class);

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

    keycloakUrl = config().getString(KEYCLOAK_URL);
    keycloakRealm = config().getString(KEYCLOAK_REALM);
    keycloakAdminClientId = config().getString(KC_ADMIN_CLIENT_ID);
    keycloakAdminClientSecret = config().getString(KC_ADMIN_CLIENT_SEC);
    keycloakAdminPoolSize = Integer.parseInt(config().getString(KC_ADMIN_POOLSIZE));

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

    KcAdmin kcadmin = new KcAdmin(keycloakUrl, keycloakRealm, keycloakAdminClientId,
        keycloakAdminClientSecret, keycloakAdminPoolSize);

    registrationService = new RegistrationServiceImpl(pool, kcadmin);
    binder = new ServiceBinder(vertx);
    consumer = binder.setAddress(REGISTRATION_SERVICE_ADDRESS)
        .register(RegistrationService.class, registrationService);

    LOGGER.debug("Info : " + LOGGER.getName() + " : Started");

  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
