package iudx.aaa.server.policy;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import io.netty.handler.codec.http.HttpResponse;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import static org.mockito.Mockito.mock;
import java.util.HashSet;
import java.util.Set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WebMockCatalogueClient {
  
  /* Mock WebClient */
  public WebClient mockWebClient(Item response) {
      WebClient mockWebClient = mock(WebClient.class);
      HttpRequest<Buffer> mockRequest = mock(HttpRequest.class);
      HttpResponse httpMockResponse = mock(HttpResponse.class);

      when(mockWebClient.get(any())).thenReturn(mockRequest);
      when(mockRequest.putHeader("content-type", "application/json")).thenReturn(mockRequest);
      doAnswer(new Answer() {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {

          /**
           * setup the response
           */
          return null;
        }

      }).when(mockRequest).sendJson(any(), any());
      return mockWebClient;
  }
}
