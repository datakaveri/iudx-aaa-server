package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.util.Urn;

/**
 * Response object to easily create a JSON response object. Objects must have <b>type</b>, and
 * <b>title</b> fields mandatorily. For error cases, <b>detail</b> must be set. For success cases,
 * either <b>arrayResults</b> or <b>objectResults</b> must be set
 */
public class Response {

  private String type;
  private String title;
  private JsonArray arrayResults;
  private JsonObject objectResults;
  private String detail;
  private JsonObject errorContext;
  
  public JsonObject getErrorContext() {
    return errorContext;
  }

  public void setErrorContext(JsonObject context) {
    this.errorContext = context;
  }

  private int status;

  public JsonObject getObjectResults() {
    return objectResults;
  }

  public void setObjectResults(JsonObject objectResults) {
    this.objectResults = objectResults;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public JsonArray getArrayResults() {
    return arrayResults;
  }

  public void setArrayResults(JsonArray arrayResults) {
    this.arrayResults = arrayResults;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getStatus() {
    return this.status;
  }

  /**
   * Convert Response object to JSON object.
   * 
   * @return a JSON object with type, title, status and detail/results
   */
  public JsonObject toJson() {
    JsonObject j = new JsonObject();
    j.put("type", this.type);
    j.put("title", this.title);
    if (this.arrayResults != null) {
      j.put("results", this.arrayResults.copy());
    }
    
    if (this.objectResults != null) {
      j.put("results", this.objectResults.copy());
    }
    
    if (this.detail != null) {
      j.put("detail", this.detail);
    }
    
    if(this.status != 0) {
      j.put("status", this.status); 
    }

    if (this.errorContext != null) {
      j.put("context", this.errorContext);
    }

    return j;
  }
  
  public String toJsonString() {
    return toJson().encode();
  }

  public Response(ResponseBuilder builder) {
    this.type = builder.type;
    this.detail = builder.detail;
    this.arrayResults = builder.arrayResults;
    this.objectResults = builder.objectResults;
    this.title = builder.title;
    this.status = builder.status;
    this.errorContext = builder.errorContext;
  }

  public static class ResponseBuilder {
    private String type;
    private String title;
    private JsonArray arrayResults = null;
    private JsonObject objectResults = null;
    private String detail = null;
    private JsonObject errorContext = null;
    private int status;

    public ResponseBuilder type(String type) {
      this.type = type;
      return this;
    }
    
    public ResponseBuilder type(Urn type) {
      this.type = type.toString();
      return this;
    }

    public ResponseBuilder title(String title) {
      this.title = title;
      return this;
    }

    public ResponseBuilder detail(String detail) {
      this.detail = detail;
      return this;
    }

    public ResponseBuilder arrayResults(JsonArray arrayResults) {
      this.arrayResults = new JsonArray();
      this.arrayResults = arrayResults.copy();
      return this;
    }

    public ResponseBuilder objectResults(JsonObject objectResults) {
      this.objectResults = new JsonObject();
      this.objectResults = objectResults.copy();
      return this;
    }

    public ResponseBuilder status(int status) {
      this.status = status;
      return this;
    }

    public ResponseBuilder errorContext(JsonObject context) {
      this.errorContext = new JsonObject();
      this.errorContext = context.copy();
      return this;
    }

    public Response build() {
      Response r = new Response(this);
      return r;
    }
  }
}
