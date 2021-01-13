package iudx.aaa.server.tip.dto;

import java.util.List;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class Response {
  private String consumer;
  private String expiry;
  private List<Request> request;
  private String consumer_certificate_class;
  
  public Response(JsonObject json) {
    this.consumer=json.getString("consumer");
    this.expiry=json.getString("expiry");
    this.request=json.getJsonArray("request").getList();
    this.consumer_certificate_class=json.getString("Consumer-certificate-class");
  }


  public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((consumer == null) ? 0 : consumer.hashCode());
    result = prime * result
        + ((consumer_certificate_class == null) ? 0 : consumer_certificate_class.hashCode());
    result = prime * result + ((expiry == null) ? 0 : expiry.hashCode());
    result = prime * result + ((request == null) ? 0 : request.hashCode());
    return result;
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Response other = (Response) obj;
    if (consumer == null) {
      if (other.consumer != null)
        return false;
    } else if (!consumer.equals(other.consumer))
      return false;
    if (consumer_certificate_class == null) {
      if (other.consumer_certificate_class != null)
        return false;
    } else if (!consumer_certificate_class.equals(other.consumer_certificate_class))
      return false;
    if (expiry == null) {
      if (other.expiry != null)
        return false;
    } else if (!expiry.equals(other.expiry))
      return false;
    if (request == null) {
      if (other.request != null)
        return false;
    } else if (!request.equals(other.request))
      return false;
    return true;
  }
  
  
}
