package iudx.aaa.server.apiserver;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Response {

  private String type, title;
  private JsonArray arrayDetail;
  private String stringDetail;
  private int status;

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

  public JsonArray getarrayDetail() {
    return arrayDetail;
  }

  public void setarrayDetail(JsonArray arrayDetail) {
    this.arrayDetail = arrayDetail;
  }

  public String getstringDetail() {
    return stringDetail;
  }

  public void setstringDetail(String stringDetail) {
    this.stringDetail = stringDetail;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getStatus() {
    return this.status;
  }

  public JsonObject toJson() {
    JsonObject j = new JsonObject();
    j.put("type", this.type);
    j.put("title", this.title);
    if (this.arrayDetail != null)
      j.put("detail", this.arrayDetail.copy());
    if (this.stringDetail != null)
      j.put("detail", this.stringDetail);
    j.put("status", this.status);

    return j;
  }

  public Response(ResponseBuilder builder) {
    this.type = builder.type;
    this.stringDetail = builder.stringDetail;
    this.arrayDetail = builder.arrayDetail;
    this.title = builder.title;
    this.status = builder.status;
  }

  public static class ResponseBuilder {
    private String type, title;
    private JsonArray arrayDetail = null;
    private String stringDetail = null;
    private int status;

    public ResponseBuilder type(String type) {
      this.type = type;
      return this;
    }

    public ResponseBuilder title(String title) {
      this.title = title;
      return this;
    }

    public ResponseBuilder stringDetail(String detail) {
      this.stringDetail = detail;
      return this;
    }

    public ResponseBuilder arrayDetail(JsonArray detail) {
      this.arrayDetail = new JsonArray();
      this.arrayDetail = detail.copy();
      return this;
    }

    public ResponseBuilder status(int status) {
      this.status = status;
      return this;
    }

    public Response build() {
      Response r = new Response(this);
      return r;
    }
  }
}
