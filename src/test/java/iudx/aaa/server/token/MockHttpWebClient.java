package iudx.aaa.server.token;

import static iudx.aaa.server.token.RequestPayload.revokeTokenValidPayload;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class MockHttpWebClient {
  
  private static TokenRevokeService httpWebClient;
  private static WebClient webClient;
  AsyncResult<JsonObject> asyncResult;
  ExecutorService executor1 = Executors.newScheduledThreadPool(10);
  
  
  @SuppressWarnings("unchecked")
  public TokenRevokeService getMockHttpWebClient() {
    if (httpWebClient == null) {
      httpWebClient = Mockito.mock(TokenRevokeService.class);
    }

    asyncResult = Mockito.mock(AsyncResult.class);
    Mockito.doAnswer((Answer<AsyncResult<JsonObject>>) arguments -> {
      ((Handler<AsyncResult<JsonObject>>) arguments.getArgument(2)).handle(asyncResult);
      return null;
    }).when(httpWebClient).httpRevokeRequest(Mockito.any(),Mockito.any(), Mockito.any());
    
    return MockHttpWebClient.httpWebClient;
    
  }

  public void setResponse(String status) {
    JsonObject response = new JsonObject();
    if ("valid".equals(status)) {
      response.put("status", "success");
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    } else {
      response.put("status", "failed");
      Mockito.when(asyncResult.cause()).thenReturn(new Throwable(response.toString()));
      Mockito.when(asyncResult.succeeded()).thenReturn(false);
      Mockito.when(asyncResult.failed()).thenReturn(true);
    }
  }
}
