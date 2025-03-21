package iudx.aaa.server.token;

import static iudx.aaa.server.apiserver.util.Urn.*;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.policy.PolicyServiceImpl;
import java.util.UUID;
import org.mockito.Mockito;

/**
 * Mocks, stubs the PolicyService. Implements the {@link
 * PolicyService#verifyResourceAccess(iudx.aaa.server.apiserver.RequestToken,
 * iudx.aaa.server.apiserver.DelegationInformation, iudx.aaa.server.apiserver.User, Handler)}
 * method.
 */
public class MockPolicyFactory {

  private static PolicyService policyService;

  public MockPolicyFactory() {
    if (policyService == null) {
      policyService = Mockito.mock(PolicyServiceImpl.class);
    }
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

      Mockito.when(policyService.verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any()))
          .thenReturn(Future.succeededFuture(response));
    } else {
      response.put("status", "failed");
      ComposeException exp =
          new ComposeException(403, URN_INVALID_INPUT, "Evaluation failed", "Evaluation failed");

      Mockito.when(policyService.verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any()))
          .thenReturn(Future.failedFuture(exp));
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

      Mockito.when(policyService.verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any()))
          .thenReturn(Future.succeededFuture(response));
    } else if ("apd-interaction".equals(status)) {
      JsonObject response = new JsonObject();
      response.put("status", "apd-interaction");
      response.put("sessionId", UUID.randomUUID().toString());
      response.put("link", itemorApdLink);
      response.put("url", url);

      Mockito.when(policyService.verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any()))
          .thenReturn(Future.succeededFuture(response));
    }
  }

  public void setResponse(JsonObject response) {
    Mockito.when(policyService.verifyResourceAccess(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(Future.succeededFuture(response));
  }
}
