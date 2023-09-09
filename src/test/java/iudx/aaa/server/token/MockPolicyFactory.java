package iudx.aaa.server.token;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.util.ComposeException;
import static iudx.aaa.server.apiserver.util.Urn.*;
import java.util.UUID;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.policy.PolicyServiceImpl;

/**
 * Mocks, stubs the PolicyService.
 * Implements the verifyPolicy method.
 */
public class MockPolicyFactory {

  private static PolicyService policyService;
  AsyncResult<JsonObject> asyncResult;

  @SuppressWarnings("unchecked")
  public MockPolicyFactory() {
    if (policyService == null) {
      policyService = Mockito.mock(PolicyServiceImpl.class);
    }

    asyncResult = Mockito.mock(AsyncResult.class);
    Mockito.doAnswer((Answer<AsyncResult<JsonObject>>) arguments -> {
      ((Handler<AsyncResult<JsonObject>>) arguments.getArgument(3)).handle(asyncResult);
      return null;
    }).when(policyService).verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  public PolicyService getInstance() {
    return MockPolicyFactory.policyService;
  }

  /**
   * Response for PolicyService mock.
   * 
   * @param status
   */
  public void setResponse(String status) {
    JsonObject response = new JsonObject();
    if ("valid".equals(status)) {
      response.put("status", "success");
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    } else {
      response.put("status", "failed");
      ComposeException exp =
          new ComposeException(403, URN_INVALID_INPUT, "Evaluation failed", "Evaluation failed");
      Mockito.when(asyncResult.cause()).thenReturn(exp);
      Mockito.when(asyncResult.succeeded()).thenReturn(false);
      Mockito.when(asyncResult.failed()).thenReturn(true);
    }
  }

  /**
   * Mock success response of verifyPolicy
   * 
   * @param status if it is a valid/apd-interaction call
   * @param item the cat ID of the item
   * @param url the url of the server
   */
  public void setResponse(String status, String itemorApdLink, String url) {
    if ("valid".equals(status)) {
    JsonObject response = new JsonObject();
      response.put("status", "success");
      response.put("cat_id", itemorApdLink);
      response.put("url", url);
      response.put("constraints", new JsonObject().put("access", new JsonArray().add("api")));
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    }
    else if ("apd-interaction".equals(status)) {
    JsonObject response = new JsonObject();
      response.put("status", "apd-interaction");
      response.put("sessionId", UUID.randomUUID().toString());
      response.put("link", itemorApdLink);
      response.put("url", url);
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    }
  }
  
  public void setResponse(JsonObject response) {
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
  }
}
