package iudx.aaa.server.token;

import static iudx.aaa.server.token.Constants.*;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Token Verticle.
 *
 * <h1>Token Verticle</h1>
 *
 * <p>The Token Verticle implementation in the the IUDX AAA Server exposes the {@link
 * iudx.aaa.server.token.TokenService} over the Vert.x Event Bus.
 *
 * @version 1.0
 * @since 2020-12-15
 */
public class TokenVerticle extends AbstractVerticle {

  /* Database Properties */
  private String databaseIP;
  private int databasePort;
  private String databaseName;
  private String databaseSchema;
  private String databaseUserName;
  private String databasePassword;
  private int poolSize;
  private PoolOptions poolOptions;
  private PgConnectOptions connectOptions;
  private PgPool pgPool;
  private TokenService tokenService;
  private JWTAuth provider;
  private PolicyService policyService;
  private RegistrationService registrationService;
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private TokenRevokeService revokeService;

  private static final Logger LOGGER = LogManager.getLogger(TokenVerticle.class);

  /**
   * This method is used to start the Verticle. It deploys a verticle in a cluster, registers the
   * service with the Event bus against an address, publishes the service with the service discovery
   * interface.
   */
  @Override
  public void start() throws Exception {

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : {} : Reading config file", LOGGER.getName());
    databaseIP = config().getString(DATABASE_IP);
    databasePort = Integer.parseInt(config().getString(DATABASE_PORT));
    databaseName = config().getString(DATABASE_NAME);
    databaseSchema = config().getString(DATABASE_SCHEMA);
    databaseUserName = config().getString(DATABASE_USERNAME);
    databasePassword = config().getString(DATABASE_PASSWORD);
    poolSize = Integer.parseInt(config().getString(POOLSIZE));
    keystorePath = config().getString(KEYSTORE_PATH);
    keystorePassword = config().getString(KEYSTPRE_PASSWORD);
    String issuer = config().getString(COS_DOMAIN, "");

    if (issuer != null && !issuer.isBlank()) {
      CLAIM_ISSUER = issuer;
    } else {
      LOGGER.fatal("Fail: cosDomain not set");
      throw new IllegalStateException("cosDomain not set");
    }

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions =
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword)
              .setConnectTimeout(PG_CONNECTION_TIMEOUT)
              .setProperties(schemaProp)
              .setReconnectAttempts(DB_RECONNECT_ATTEMPTS)
              .setReconnectInterval(DB_RECONNECT_INTERVAL_MS);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Initializing the services */
    provider = jwtInitConfig();
    revokeService = new TokenRevokeService(vertx);
    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
    policyService = PolicyService.createProxy(vertx, POLICY_SERVICE_ADDRESS);
    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    tokenService =
        new TokenServiceImpl(pgPool, policyService, registrationService, provider, revokeService);
    binder = new ServiceBinder(vertx);
    consumer = binder.setAddress(TOKEN_SERVICE_ADDRESS).register(TokenService.class, tokenService);

    LOGGER.debug("Info : {} : Started", LOGGER.getName());
  }

  /**
   * Initializes {@link JWTAuth} to create a Authentication Provider instance for JWT token.
   * Authentication Provider is used to generate and authenticate JWT token.
   *
   * @return provider
   */
  public JWTAuth jwtInitConfig() {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(new KeyStoreOptions().setPath(keystorePath).setPassword(keystorePassword));

    JWTAuth provider = JWTAuth.create(vertx, config);
    return provider;
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }
}
