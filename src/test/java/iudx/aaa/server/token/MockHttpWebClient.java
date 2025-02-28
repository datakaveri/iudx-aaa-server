package iudx.aaa.server.token;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.mockito.Mockito;

public class MockHttpWebClient {

  private static TokenRevokeService httpWebClient;

  public TokenRevokeService getMockHttpWebClient() {
    if (httpWebClient == null) {
      httpWebClient = Mockito.mock(TokenRevokeService.class);
    }

    return MockHttpWebClient.httpWebClient;
  }

  public void setResponse(String status) {
    JsonObject response = new JsonObject();
    if ("valid".equals(status)) {
      response.put("status", "success");
      Mockito.when(httpWebClient.httpRevokeRequest(Mockito.any(), Mockito.any()))
          .thenReturn(Future.succeededFuture(response));
    } else {
      response.put("status", "failed");
      Mockito.when(httpWebClient.httpRevokeRequest(Mockito.any(), Mockito.any()))
          .thenReturn(Future.failedFuture(response.toString()));
    }
  }
}
