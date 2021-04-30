package iudx.aaa.server.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CatalogueClientTest {

  private static Logger LOGGER = LoggerFactory.getLogger(PolicyServiceTest.class);

  private static Vertx vertxObj;
  private static WebClient client;
  // private static Item item;

  @Mock
  private static CatalogueClient catMock;

  // @Mock
  private static Item item;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, io.vertx.reactivex.core.Vertx vertx2,
      VertxTestContext testContext) {

    vertxObj = vertx;
    
    /* prepare items */
    catMock = new CatalogueClient();
    Set<String> servers = new HashSet<>();

    item = new Item();
    item.setId(
        "datakaveri.org/f7e044eee8122b5c87dce6e7ad64f3266afa41dc/rs.iudx.io/aqm-bosch-climo/PuneRailwayStation_28");
    item.setProviderID("datakaveri.org/f7e044eee8122b5c87dce6e7ad64f3266afa41dc");
    item.setType("iudx:Resource");
    item.setServers(servers);

    testContext.completeNow();
  }

  @Test
  @DisplayName("Valid FetchItem")
  void fetchItemTest(VertxTestContext testContext) {

    String id = item.getId();
    String type = item.getType();

    Mockito.when(catMock.fetchItem(id, type)).thenReturn(item);
    assertEquals(
        "datakaveri.org/f7e044eee8122b5c87dce6e7ad64f3266afa41dc/rs.iudx.io/aqm-bosch-climo/PuneRailwayStation_28",
        catMock.fetchItem(id, type).getId());
    
    testContext.completeNow();
  }
  
  @Test
  @DisplayName("Invalid FetchItem")
  void invalidFetchItemTest(VertxTestContext testContext) {

    String id = item.getId();
    String type = item.getType();

    Mockito.when(catMock.fetchItem(id, type)).thenReturn(item);
    assertNotEquals(
        "datakaveri.org/f7e044eee8122b5cq87dce6e7ad64f3266afa41dc/rs.iudx.io/aqm-bosch-climo/PuneRailwayStation_28",
        catMock.fetchItem(id, type).getId());
    
    testContext.completeNow();
  }
}
