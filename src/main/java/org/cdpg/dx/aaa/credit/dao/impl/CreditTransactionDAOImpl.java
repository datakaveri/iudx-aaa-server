package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.CreditTransactionDAO;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.aaa.credit.models.CreditTransactionDTO;
import org.cdpg.dx.aaa.credit.models.TransactionType;
import org.cdpg.dx.aaa.credit.util.Constants;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CreditTransactionDAOImpl implements CreditTransactionDAO {

  private static final Logger LOGGER = LogManager.getLogger(CreditTransactionDAOImpl.class);
  private final PostgresService postgresService;

  public CreditTransactionDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }


  @Override
  public Future<Boolean> logTransaction(CreditTransaction creditTransaction) {

    InsertQuery insertQuery = new InsertQuery(
      Constants.CREDIT_TRANSACTION_TABLE,
      List.copyOf(creditTransaction.toNonEmptyFieldsMap().keySet()),
      List.copyOf(creditTransaction.toNonEmptyFieldsMap().values())
      );


    LOGGER.debug("Insert Query: {}", insertQuery.toSQL());
    LOGGER.debug("Query Params: {}", insertQuery.getQueryParams());

    return postgresService.insert(insertQuery)
      .compose(result -> {
        if (result.getRows().isEmpty()) {
          return Future.failedFuture("Insert query returned no rows.");
        }
        return Future.succeededFuture(true);
      })
      .recover(err -> {
        LOGGER.error("Error inserting organization create request: {}", err.getMessage(), err);
        return Future.failedFuture(err);
      });
  }
}
