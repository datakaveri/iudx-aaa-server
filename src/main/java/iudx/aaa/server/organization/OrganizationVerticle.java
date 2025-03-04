package iudx.aaa.server.organization;


import com.hazelcast.config.Config;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import iudx.aaa.server.database.postgres.service.PostgresService;
import iudx.aaa.server.organization.service.OrganizationService;
import iudx.aaa.server.organization.service.OrganizationServiceImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static iudx.aaa.server.util.Constants.ORGANIZATION_SERVICE_ADDRESS;
import static iudx.aaa.server.util.Constants.PG_SERVICE_ADDRESS;

public class OrganizationVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationVerticle.class);
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  PostgresService postgresService;


  @Override
  public void start() throws Exception {
    binder = new ServiceBinder(vertx);
    postgresService = PostgresService.createProxy(vertx, PG_SERVICE_ADDRESS);
    OrganizationService organizationService = new OrganizationServiceImpl(postgresService);

    consumer = binder.setAddress(ORGANIZATION_SERVICE_ADDRESS).register(OrganizationService.class, organizationService);
    LOGGER.info("Organization Service Started");
  }

  @Override
  public void stop() {
    binder.unregister(consumer);
  }

}



