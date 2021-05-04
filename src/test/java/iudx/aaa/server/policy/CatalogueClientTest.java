package iudx.aaa.server.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CatalogueClientTest {

  private static Logger LOGGER = LoggerFactory.getLogger(PolicyServiceTest.class);

  private static Vertx vertxObj;
  private static WebMockCatalogueClient client;
  // private static Item item;

  private static Item item;
  private static CatalogueClient catMock;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, io.vertx.reactivex.core.Vertx vertx2,
      VertxTestContext testContext) {
    
    /* prepare items */
    client = new WebMockCatalogueClient();
    catMock = MockCatalogueFactory.MockCatalogueFactory();

    testContext.completeNow();
  }

  @Test
  @DisplayName("Valid FetchItem")
  void fetchItemTest(VertxTestContext testContext) {

    Item item = catMock.fetchItem("datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.org.in/pune-env-flood");
    assertEquals(
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.org.in/pune-env-flood",item.getId());
    
    testContext.completeNow();
  }
  
  @Test
  @DisplayName("Invalid FetchItem")
  void invalidFetchItemTest(VertxTestContext testContext) {
       
    Item item = catMock.fetchItem("suratmunicipal.org/6db486cb4f720e8585ba1f45a931c63c25dbbbda/rs.iudx.org.in/wrong-id");
    assertNotEquals("suratmunicipal.org/6db486cb4f720e8585ba1f45a931c63c25dbbbda/rs.iudx.org.in/wrong-id", item.getId());
    
    testContext.completeNow();
  }
}
