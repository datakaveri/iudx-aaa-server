package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.UUID;

public class UserCreditDAOImpl implements UserCreditDAO {
  private final PostgresService postgresService;

  public UserCreditDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }

  @Override
  public Future<UserCredit> getById(UUID userId) {
    return null;
  }

  @Override
  public Future<Boolean> add(UUID userId, double amount) {
    return null;
  }

  @Override
  public Future<Boolean> deduct(UUID userId, double amount) {
    return null;
  }

  @Override
  public Future<Boolean> update(UserCreditDTO userCreditDTO) {
    return null;
  }

  @Override
  public Future<UserCredit> get(UUID userId) {
    return null;
  }
}
