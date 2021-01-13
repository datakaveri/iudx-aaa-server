package iudx.aaa.server.tip.dto;

import java.util.List;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class Request {
  private String id;
  private List<String> apis;
  private String body;
  private List<String> methods;


  public Request(JsonObject json) {
    this.id = json.getString("id");
    this.apis = json.getJsonArray("apis").getList();
    this.body = json.getString("body");
    this.methods = json.getJsonArray("methods").getList();
  }


  public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((apis == null) ? 0 : apis.hashCode());
    result = prime * result + ((body == null) ? 0 : body.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((methods == null) ? 0 : methods.hashCode());
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
    Request other = (Request) obj;
    if (apis == null) {
      if (other.apis != null)
        return false;
    } else if (!apis.equals(other.apis))
      return false;
    if (body == null) {
      if (other.body != null)
        return false;
    } else if (!body.equals(other.body))
      return false;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    if (methods == null) {
      if (other.methods != null)
        return false;
    } else if (!methods.equals(other.methods))
      return false;
    return true;
  }



}
