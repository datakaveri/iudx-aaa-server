package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class OrderBy {
    public enum Direction { ASC, DESC }

    private String column;
    private Direction direction;

    // Default constructor (Needed for deserialization)
    public OrderBy() {}

    // Constructor
    public OrderBy(String column, Direction direction) {
        this.column = column;
        this.direction = direction;
    }

    // JSON Constructor
    public OrderBy(JsonObject json) {
        OrderByConverter.fromJson(json, this); // Use the generated converter
    }

    // Convert to JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        OrderByConverter.toJson(this, json);
        return json;
    }

    // Getters & Setters
    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    // SQL Representation
    public String toSQL() {
        return column + " " + direction.name();
    }
}