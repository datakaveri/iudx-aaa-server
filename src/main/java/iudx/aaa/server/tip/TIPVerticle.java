package iudx.aaa.server.tip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
import static iudx.aaa.server.tip.Constants.*;

/**
 * The TIP Verticle.
 * <h1>TIP Verticle</h1>
 * <p>
 * The TIP Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.tip.TIPService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 * @since 2020-12-15
 */

public class TIPVerticle extends AbstractVerticle {

  /* Database Properties */
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private PgPool pgclient;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;
  private PostgresClient pgClient;
  private TIPService tipService;
  private String keystorePath;
  private String keystorePassword;
  private JWTAuth provider;
  private PolicyService policyService;
  
  private static final Logger LOGGER = LogManager.getLogger(TIPVerticle.class);

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, registers the
   * service with the Event bus against an address, publishes the service with the service discovery
   * interface.
   */

  @Override
  public void start() throws Exception {

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : " + LOGGER.getName() + " : Reading config file");

    databaseIP = config().getString("databaseIP");
    databasePort = Integer.parseInt(config().getString("databasePort"));
    databaseName = config().getString("databaseName");
    databaseUserName = config().getString("databaseUserName");
    databasePassword = config().getString("databasePassword");
    poolSize = Integer.parseInt(config().getString("poolSize"));
    keystorePath = config().getString("keystorePath");
    keystorePassword = config().getString("keystorePassword");

    /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */
    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);
    
    pgClient = new PostgresClient(vertx, connectOptions, poolOptions);
    provider = jwtInitConfig();
    policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
    tipService = new TIPServiceImpl(pgClient,policyService, provider);

    new ServiceBinder(vertx).setAddress(TIP_SERVICE_ADDRESS).register(TIPService.class, tipService);

    LOGGER.debug("Info : " + LOGGER.getName() + " : Started");

  }
  
  /**
   * Initializes {@link JWTAuth} to create a Authentication Provider instance for JWT token.
   * Authentication Provider is used to generate and authenticate JWT token. 
   * @return provider
   */
  public JWTAuth jwtInitConfig() {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(
        new KeyStoreOptions()
          .setPath(keystorePath)
          .setPassword(keystorePassword));

    JWTAuth provider = JWTAuth.create(vertx, config);
    return provider;
  }
}
