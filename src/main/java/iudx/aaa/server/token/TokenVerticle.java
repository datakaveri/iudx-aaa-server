package iudx.aaa.server.token;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AbstractVerticle;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.postgres.client.PostgresClient;

/**
 * The Token Verticle.
 * <h1>Token Verticle</h1>
 * <p>
 * The Token Verticle implementation in the the IUDX AAA Server exposes the
 * {@link iudx.aaa.server.token.TokenService} over the Vert.x Event Bus.
 * </p>
 *
 * @version 1.0
 * @since 2020-12-15
 */

public class TokenVerticle extends AbstractVerticle {

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
  private static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";
  private TokenService tokenService;
  private static final Logger LOGGER = LogManager.getLogger(TokenVerticle.class);

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

    tokenService = new TokenServiceImpl(pgClient);

    new ServiceBinder(vertx).setAddress(TOKEN_SERVICE_ADDRESS).register(TokenService.class,
        tokenService);

    LOGGER.debug("Info : " + LOGGER.getName() + " : Started");

  }

}
