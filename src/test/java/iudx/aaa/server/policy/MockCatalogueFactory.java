package iudx.aaa.server.policy;

import java.util.HashSet;
import java.util.Set;
import org.mockito.Mockito;

public class MockCatalogueFactory {

  private CatalogueClient client;

  public static CatalogueClient MockCatalogueFactory() {

    CatalogueClient client = Mockito.mock(CatalogueClient.class);
    setBehaviour(client);
    
    return client;
  }

  public static void setBehaviour(CatalogueClient client) {

    Mockito.when(client.fetchItem(prepareItem1().getId())).thenReturn(prepareItem1());
    Mockito.when(client.fetchItem(prepareItem2().getId())).thenReturn(prepareItem2());
  }

  public static Item prepareItem1() {

    Item item1 = new Item();
    Set<String> servers1 = new HashSet<>();
    servers1.add("datakaveri.org/27e503da0bdda6efae3a52b3ef423c1f9005657a/file.iudx.org.in");
    servers1.add("datakaveri.org/27e503da0bdda6efae3a52b3ef423c1f9005657a/rs.iudx.org.in");

    item1.setId(
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.org.in/pune-env-flood");
    item1.setProviderID("datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b");
    item1.setType("iudx:Resource");
    item1.setServers(servers1);

    return item1;
  }

  public static Item prepareItem2() {

    Item item2 = new Item();
    Set<String> servers2 = new HashSet<>();
    servers2.add("datakaveri.org/27e503da0bdda6efae3a52b3ef423c1f9005657a/rs.iudx.org.in");

    item2.setId(
        "suratmunicipal.org/6db486cb4f720e8585ba1f45a931c63c25dbbbda/rs.iudx.org.in/surat-itms-realtime-info");
    item2.setProviderID("suratmunicipal.org/6db486cb4f720e8585ba1f45a931c63c25dbbbda");
    item2.setType("iudx:ResourceGroup");
    item2.setServers(servers2);

    return item2;
  }
}
