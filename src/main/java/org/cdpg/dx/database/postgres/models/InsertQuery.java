package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

@DataObject(generateConverter = true)
public class InsertQuery implements Query {
    private String table;
    private List<String> columns;
    private List<Object> values;

    // Default constructor (Needed for deserialization)
    public InsertQuery() {}

    // Constructor
    public InsertQuery(String table, List<String> columns, List<Object> values) {
        this.table = table;
        this.columns = columns;
        this.values = values;
    }

    // JSON Constructor
    public InsertQuery(JsonObject json) {
        InsertQueryConverter.fromJson(json, this);  // Use generated converter
    }

    // Convert to JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        InsertQueryConverter.toJson(this, json);
        return json;
    }

    // Getters & Setters (Required for DataObject)
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<Object> getValues() { return values; }
    public void setValues(List<Object> values) { this.values = values; }

    @Override
    public String toSQL() {
//        String placeholders = "?,".repeat(columns.size()).replaceAll(",$", "");
//        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";

      String placeholders = columns.stream().map(col -> "?").collect(Collectors.joining(", "));
      System.out.println("Placeholders: "+placeholders);
      String finalQuery= "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
      System.out.println("Final Query: "+finalQuery);
      return finalQuery;
    }

    @Override
    public List<Object> getQueryParams() {
        return values;
    }
}
