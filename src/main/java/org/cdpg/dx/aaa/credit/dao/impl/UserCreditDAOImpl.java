package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationCreateRequestDAOImpl;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserCreditDAOImpl implements UserCreditDAO {
  private final PostgresService postgresService;
  private static final Logger LOGGER = LogManager.getLogger(UserCreditDAOImpl.class);


  public UserCreditDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }


  @Override
  public Future<Double> getBalance(UUID userId) {
    Condition condition = new Condition(Constants.USER_ID , Condition.Operator.EQUALS, List.of(userId.toString()));

    SelectQuery query = new SelectQuery(
      Constants.USER_CREDIT_TABLE,
      List.of(Constants.BALANCE),
      condition, null, null, null, null
    );

    return postgresService.select(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("No balance found for userId: " + userId);
        }
        JsonObject row = result.getRows().getJsonObject(0);
        double balance = row.getDouble(Constants.BALANCE);
        return Future.succeededFuture(balance);
      })
      .recover(err -> {
        LOGGER.error("Error fetching all organizations: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<Boolean> updateBalance(UUID userId, double updatedAmount) {
    Condition condition = new Condition(Constants.USER_ID , Condition.Operator.EQUALS, List.of(userId.toString()));

    UpdateQuery query = new UpdateQuery(
      Constants.USER_CREDIT_TABLE,
      List.of(Constants.BALANCE,Constants.UPDATED_AT),
      List.of(updatedAmount, Instant.now().toString()),
      condition,null,null
    );

    return postgresService.update(query)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Update query returned no rows.");
        }
        return Future.succeededFuture(true);
      })
      .recover(err -> {
        LOGGER.error("Error updating request status: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }


}


