package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@DataObject(generateConverter = true)
public class UpdateQuery implements Query {
    private  String table;
    private List<String> columns;
    private  List<Object> values;
    private  ConditionComponent condition;
    private  List<OrderBy> orderBy;
    private  Integer limit;

    //    public UpdateQuery(String table, List<String> columns, List<Object> values,
//                       ConditionComponent condition, List<OrderBy> orderBy, Integer limit) {
//        this.table = Objects.requireNonNull(table, "Table name cannot be null");
//        this.columns = Objects.requireNonNull(columns, "Columns cannot be null");
//        this.values = Objects.requireNonNull(values, "Values cannot be null");
//        this.condition = condition;
//        this.orderBy = orderBy;
//        this.limit = limit;
//    }

    public UpdateQuery(String table, List<String> columns, List<Object> values,
                       ConditionComponent condition, List<OrderBy> orderBy, Integer limit) {
        this.table = Objects.requireNonNull(table, "Table name cannot be null");
        this.columns = Objects.requireNonNull(columns, "Columns cannot be null");
        this.values = Objects.requireNonNull(values, "Values cannot be null");
        this.condition = condition;
        this.orderBy = orderBy;
        this.limit = limit;
    }

    public UpdateQuery(UpdateQuery other) {
        this.table = other.getTable();
        this.columns = other.getColumns();
        this.values = other.getValues();
        this.condition = other.getCondition();
        this.orderBy = other.getOrderBy();
        this.limit = other.getLimit();
    }
    public UpdateQuery(){}

    public UpdateQuery(JsonObject json) {
        UpdateQueryConverter.fromJson(json, this);
        this.table = json.getString("table");
        this.columns = json.getJsonArray("columns").getList();
        this.values = json.getJsonArray("values").getList();
        this.condition = json.containsKey("condition") ? new Condition(json.getJsonObject("condition")) : null;
        this.orderBy = json.containsKey("orderBy") ? json.getJsonArray("orderBy").stream()
                .map(obj -> new OrderBy((JsonObject) obj))
                .collect(Collectors.toList()) : null;
        this.limit = json.getInteger("limit");
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        UpdateQueryConverter.toJson(this, json);
        return json;
    }

    @Override
    public String toSQL() {
        StringBuilder query = new StringBuilder("UPDATE ").append(table).append(" SET ");
        query.append(columns.stream().map(column -> column + " = ?").collect(Collectors.joining(", ")));

        if (condition != null) query.append(" WHERE ").append(condition.toSQL());
        if (orderBy != null && !orderBy.isEmpty()) {
            query.append(" ORDER BY ")
                    .append(orderBy.stream().map(OrderBy::toSQL).collect(Collectors.joining(", ")));
        }
        if (limit != null) query.append(" LIMIT ").append(limit);

        return query.toString();
    }

    @Override
    public List<Object> getQueryParams() {
        List<Object> params = values;
        if (condition != null) params.addAll(condition.getQueryParams());
        return params;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public ConditionComponent getCondition() {
        return condition;
    }

    public void setCondition(ConditionComponent condition) {
        this.condition = condition;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public List<OrderBy> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderBy> orderBy) {
        this.orderBy = orderBy;
    }
}
