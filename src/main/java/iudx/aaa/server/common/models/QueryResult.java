package iudx.aaa.server.common.models;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class QueryResult {
    private JsonArray rows;
    private String error;

    public QueryResult(JsonArray rows) {
        this.rows = rows;
        this.error = null;
    }

    public QueryResult(String error) {
        this.rows = new JsonArray();
        this.error = error;
    }

    public QueryResult(List<JsonObject> rows) {
        this.rows = new JsonArray(rows); // Convert List<JsonObject> to JsonArray
        this.error = null;
    }
    public QueryResult(JsonObject json) {
        this.rows = json.getJsonArray("rows"); // Convert List<JsonObject> to JsonArray
        this.error = null;
    }

    public JsonArray getRows() {
        return rows;
    }

    public String getError() {
        return error;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("rows", rows);
        if (error != null) {
            json.put("error", error);
        }
        return json;
    }
}
