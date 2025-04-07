package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@DataObject(generateConverter = true)
public class Condition implements ConditionComponent {
    private String column;
    private Operator operator;
    private List<Object> values;

    public enum Operator {
        EQUALS("="), NOT_EQUALS("!="), GREATER(">"), LESS("<"), GREATER_EQUALS(">="), LESS_EQUALS("<="),
        LIKE("LIKE"), IN("IN"), NOT_IN("NOT IN"), BETWEEN("BETWEEN"),
        IS_NULL("IS NULL"), IS_NOT_NULL("IS NOT NULL");

        private final String symbol;
        Operator(String symbol) { this.symbol = symbol; }
        public String getSymbol() { return symbol; }
    }
    public Condition(){}
    public Condition(Condition other){
        this.column = other.getColumn();
        this.values = other.getValues();
        this.operator = other.getOperator();
    }

    public Condition(String column, Operator operator, List<Object> values) {
        this.column = Objects.requireNonNull(column, "Column cannot be null");
        this.operator = Objects.requireNonNull(operator, "Operator cannot be null");
        this.values = values; // Can be null for IS NULL and IS NOT NULL cases
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getColumn() {
        return column;
    }

    public List<Object> getValues() {
        return values;
    }

    public Operator getOperator() {
        return operator;
    }

    public Condition(JsonObject json) {
        ConditionConverter.fromJson(json, this);
        this.column = json.getString("column");
        this.operator = Operator.valueOf(json.getString("operator"));
        this.values = json.containsKey("values") ? json.getJsonArray("values").getList() : null;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        ConditionConverter.toJson(this, json);
        return json;
    }

    @Override
    public String toSQL() {
        return switch (operator) {
            case EQUALS, NOT_EQUALS, GREATER, LESS, GREATER_EQUALS, LESS_EQUALS, LIKE ->
                    column + " " + operator.getSymbol() + " $1";
            case IN, NOT_IN ->
                    column + " " + operator.getSymbol() + " (" +
                            values.stream().map(v -> "?").collect(Collectors.joining(", ")) + ")";
            case BETWEEN ->
                    column + " BETWEEN ? AND ?";
            case IS_NULL, IS_NOT_NULL ->
                    column + " " + operator.getSymbol();
        };
    }

    @Override
    public List<Object> getQueryParams() {
        return values == null ? List.of() : values;
    }
}
