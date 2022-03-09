package iudx.aaa.server.apiserver.util;

import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;

/**
 * ComposeException can be used when failing futures in services or methods called by services.
 * Since Vert.x allows to fail a future using a Throwable instead of a string, this allows the
 * Throwable to be caught and handled at the end of a compose chain in an onFailure() block The
 * exception uses the {@link iudx.aaa.server.apiserver.Response} object, which allows the error
 * response to be created and passed on in the event of the particular failure. The Response can
 * then be sent back to the API server when the exception is caught and handled.
 */
public class ComposeException extends Exception {

  private static final long serialVersionUID = 1L;

  private final Response response;

  /**
   * Create a new ComposeException using a Response object (preferably representing an error). The
   * title field of the Response object is used as the message for the Exception.
   * 
   * @param response The Response object
   */
  public ComposeException(Response response) {
    super(response.getTitle());
    this.response = response;
  }

  /**
   * Create a new ComposeException with the parameters needed to create an Response object
   * representing an error. The title field is used as the Exception message.
   * 
   * @param status The HTTP status code
   * @param type The appropriate URN
   * @param title The appropriate title
   * @param detail The appropriate reason for the error
   */
  public ComposeException(int status, String type, String title, String detail) {
    super(title);
    this.response =
        new ResponseBuilder().status(status).type(type).title(title).detail(detail).build();
  }


    /**
   * Create a new ComposeException with the parameters needed to create an Response object
   * representing an error. The title field is used as the Exception message.
   *
   * @param status The HTTP status code
   * @param type The appropriate URN
   * @param title The appropriate title
   * @param detail The appropriate reason for the error
   */
  public ComposeException(int status, Urn type, String title, String detail) {
    super(title);
    this.response =
            new ResponseBuilder().status(status).type(type).title(title).detail(detail).build();
  }

  public Response getResponse() {
    return response;
  }


}
