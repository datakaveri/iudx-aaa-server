package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;

public class CreditRequestDAOImpl implements CreditRequestDAO {
  private final PostgresService postgresService;

  public CreditRequestDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }

  @Override
  public Future<CreditRequest> create(UUID userId, double amount) {
    return null;
  }

  @Override
  public Future<CreditRequest> getById(UUID requestId) {
    return null;
  }

  @Override
  public Future<List<CreditRequest>> getAll(Status status) {
    return null;
  }

  @Override
  public Future<Boolean> updateStatus(UUID requestId, Status status) {
    return null;
  }
}
