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

  //TODO: Partly Working; Need to be Optimized
  @SuppressWarnings({"unused", "unchecked"})
  public WebClient getMockWebClient() {

    if (webClient == null) {
      webClient = Mockito.mock(WebClient.class);
    }

    /* Handling the WebClient call to KeyCloak */
    HttpRequest<Buffer> httpRequest1 = Mockito.mock(HttpRequest.class);
    HttpResponse<Buffer> httpResponse1 = Mockito.mock(HttpResponse.class);
    RequestOptions options1 = Mockito.mock(RequestOptions.class);
   // Mockito.when(webClient.request(Mockito.any(HttpMethod.class), Mockito.any(RequestOptions.class))).thenReturn(httpRequest1);

    AsyncResult<HttpResponse<Buffer>> asyncResult1 = Mockito.mock(AsyncResult.class);
    Mockito.when(asyncResult1.succeeded()).thenReturn(true);
    Mockito.when(asyncResult1.result()).thenReturn(httpResponse1);
    Mockito.when(httpResponse1.bodyAsJsonObject())
        .thenReturn(revokeTokenValidPayload.put("access_token", "token"));

    Mockito.doAnswer(invocationOnMock -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocationOnMock.getArgument(1);
      executor1.submit(() -> handler.handle(asyncResult1));
      return null;
    }).when(httpRequest1).sendForm(Mockito.any(MultiMap.class), Mockito.any());
    
    Mockito.when(asyncResult1.failed()).thenReturn(false);
    /* Handling the WebClient call to ResourceServer */
    HttpRequest<Buffer> httpRequest2 = Mockito.mock(HttpRequest.class);
    HttpResponse<Buffer> httpResponse2 = Mockito.mock(HttpResponse.class);
    RequestOptions options2 = Mockito.mock(RequestOptions.class);
    
    //Mockito.when(webClient.request(HttpMethod.POST, options2)).thenReturn(httpRequest2);
    
    List<HttpRequest<Buffer>> answers = Arrays.asList(httpRequest1, httpRequest2);
    Mockito.when(webClient.request(Mockito.any(HttpMethod.class), Mockito.any(RequestOptions.class))).thenAnswer(AdditionalAnswers.returnsElementsOf(answers));


    
    AsyncResult<HttpResponse<Buffer>> asyncResult2 = Mockito.mock(AsyncResult.class);
    Mockito.when(asyncResult2.succeeded()).thenReturn(true);
    Mockito.when(asyncResult2.result()).thenReturn(httpResponse2);
    
    Mockito.when(asyncResult2.failed()).thenReturn(true);
    Mockito.when(asyncResult2.cause()).thenReturn(new Throwable("ss"));
    
    Mockito.when(httpResponse2.bodyAsJsonObject())
        .thenReturn(revokeTokenValidPayload.copy().clear().put("status", "success"));
        
    Mockito.doAnswer(invocationOnMock -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocationOnMock.getArgument(1);
      executor1.submit(() -> handler.handle(asyncResult2));
      return null;
    }).when(httpRequest2).sendJsonObject(Mockito.any(JsonObject.class), Mockito.any());

    return webClient;
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
