package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.util.Constants.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


public class RespBuilder {
  private JsonObject response = new JsonObject();

  public RespBuilder withType(String type) {
    response.put(TYPE, type);
    return this;
  }

  public RespBuilder withTitle(String title) {
    response.put(TITLE, title);
    return this;
  }

  public RespBuilder withDetail(String detail) {
    response.put(DETAIL, detail);
    return this;
  }

  /**
   * Builds a response object with the given result attributes and adds it to the current response.
   * @param id the ID of the result
   * @param method The method associated with the result
   * @param status The status of the result
   * @return The response builder instance with the result added.
   */
  public RespBuilder withResult(String id, String method, String status) {
    JsonObject resultAttrs = new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status);
    response.put(RESULTS, new JsonArray().add(resultAttrs));
    return this;
  }

  /**
   * Builds a response object with the given result attributes and adds it to the current response.
   * @param id The ID of the result.
   * @param method The method associated with the result.
   * @param status  The status of the result.
   * @param detail The detailed description of the result.
   * @return The response builder instance with the result added.
   */
  public RespBuilder withResult(String id, String method, String status, String detail) {
    JsonObject resultAttrs =
        new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status).put(DETAIL, detail);
    response.put(RESULTS, new JsonArray().add(resultAttrs));
    return this;
  }

  public RespBuilder withResult() {
    response.put(RESULTS, new JsonArray());
    return this;
  }

  public RespBuilder withResult(JsonArray resultArray) {
    response.put(RESULTS, resultArray);
    return this;
  }

  public RespBuilder withResult(String resultJsonArray) {
    response.put(RESULTS, new JsonArray(resultJsonArray));
    return this;
  }

  public RespBuilder totalHits(JsonArray jsonArray) {
    totalHits(jsonArray.toString());
    return this;
  }

  /**
   *Sets the total number of hits for the response, based on the size of the given JSON array.
   * @param resultJsonArray  The JSON array of results.
   * @return The response builder instance with the total hits set.
   */

  public RespBuilder totalHits(String resultJsonArray) {
    JsonArray jsonArray = new JsonArray(resultJsonArray);
    int size = jsonArray.size();
    response.put(TOTAL_HITS, size);
    return this;
  }

  public JsonObject getJsonResponse() {
    return response;
  }

  public String getResponse() {
    return response.toString();
  }
}
