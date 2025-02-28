package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.registration.RegistrationService;
import org.mockito.Mockito;

/** Mocks, stubs the RegistrationService. Implements the getUserDetails method. */
public class MockRegistrationFactory {

  private static RegistrationService registrationService;

  public MockRegistrationFactory() {
    if (registrationService == null) {
      registrationService = Mockito.mock(RegistrationService.class);
    }
  }

  public RegistrationService getInstance() {
    return MockRegistrationFactory.registrationService;
  }

  /**
   * Response for RegistrationService mock.
   *
   * @param status
   */
  public void setResponse(String status) {
    JsonObject response = new JsonObject();
    JsonObject obj = new JsonObject();
    if ("valid".equals(status)) {
      obj.put("name", "abc").put("email", "abc@xyz.com");
      response.put("d34b1547-7281-4f66-b550-ed79f9bb0c36", obj);
      Mockito.when(registrationService.getUserDetails(Mockito.any()))
          .thenReturn(Future.succeededFuture(response));
    } else {
      Mockito.when(registrationService.getUserDetails(Mockito.any()))
          .thenReturn(Future.failedFuture("failed"));
    }
  }

  /**
   * Returns expected Map result to show successful flow.
   *
   * @param response is void
   */
  public void setResponse(JsonObject response) {
    Mockito.when(registrationService.getUserDetails(Mockito.any()))
        .thenReturn(Future.succeededFuture(response));
  }
}
