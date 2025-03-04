package iudx.aaa.server.common.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class Query {

    private String sql;
    private JsonArray params;

    // Required by @DataObject for deserialization and reflection
    public Query() {
    }

    public Query(String sql, JsonArray params) {
        this.sql = sql;
        this.params = params;
    }

    public String getSql() {
        return sql;
    }

    public Query setSql(String sql) {
        this.sql = sql;
        return this;
    }

    public JsonArray getParams() {
        return params;
    }

    public Query setParams(JsonArray params) {
        this.params = params;
        return this;
    }

    // Serializer method
    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("sql", this.sql);
        if (this.params != null) {
            json.put("params", this.params);
        }
        return json;
    }

    // Constructor for @DataObject
    public Query(JsonObject json) {
        this.sql = json.getString("sql");
        this.params = json.getJsonArray("params");
    }
}
