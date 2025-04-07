package org.cdpg.dx.database.postgres.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import java.util.List;

@DataObject
@JsonGen(publicConverter = false)
public interface ConditionComponent {

    String toSQL();


    List<Object> getQueryParams();
}
