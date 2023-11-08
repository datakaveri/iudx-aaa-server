package iudx.aaa.server.policy;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.registration.RegistrationService;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/** Mocks, stubs the RegistrationService. Implements the getUserDetails method. */
public class MockRegistrationFactory {

  private static RegistrationService registrationService;
  AsyncResult<JsonObject> asyncResult;

  @SuppressWarnings("unchecked")
  public MockRegistrationFactory() {
    if (registrationService == null) {
      registrationService = Mockito.mock(RegistrationService.class);
    }

    asyncResult = Mockito.mock(AsyncResult.class);
    Mockito.doAnswer(
            (Answer<AsyncResult<JsonObject>>)
                arguments -> {
                  ((Handler<AsyncResult<JsonObject>>) arguments.getArgument(1)).handle(asyncResult);
                  return null;
                })
        .when(registrationService)
        .getUserDetails(Mockito.any(), Mockito.any());
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
      Mockito.when(asyncResult.result()).thenReturn(response);
      Mockito.when(asyncResult.failed()).thenReturn(false);
      Mockito.when(asyncResult.succeeded()).thenReturn(true);
    } else {

      Mockito.when(asyncResult.cause()).thenReturn(new Throwable("failed"));
      Mockito.when(asyncResult.succeeded()).thenReturn(false);
      Mockito.when(asyncResult.failed()).thenReturn(true);
    }
  }

  /**
   * Returns expected Map result as an AsyncResult to show successful flow.
   *
   * @param response is void
   */
  public void setResponse(JsonObject response) {
    Mockito.when(asyncResult.result()).thenReturn(response);
    Mockito.when(asyncResult.failed()).thenReturn(false);
    Mockito.when(asyncResult.succeeded()).thenReturn(true);
  }
}
