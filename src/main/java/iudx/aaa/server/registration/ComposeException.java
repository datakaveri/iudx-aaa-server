package iudx.aaa.server.registration;

import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;

public class ComposeException extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  private final Response response;

  public ComposeException(Response response) {
    super(response.getTitle());
    this.response = response;
  }

  public ComposeException(int status, String type, String title, String detail) {
    super(title);
    this.response =
        new ResponseBuilder().status(status).type(type).title(title).detail(detail).build();
  }

  public Response getResponse() {
    return response;
  }


}
