package org.cdpg.dx.aaa.credit.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.dao.CreditDeductionDAO;
import org.cdpg.dx.aaa.credit.models.CreditDeduction;
import org.cdpg.dx.aaa.credit.models.CreditDeductionDTO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;

public class CreditDeductionDAOImpl implements CreditDeductionDAO {
  private final PostgresService postgresService;

  public CreditDeductionDAOImpl(PostgresService postgresService)
  {
    this.postgresService = postgresService;
  }


  @Override
  public Future<Boolean> log(CreditDeductionDTO creditDeductionDTO) {
    return null;
  }

  @Override
  public Future<List<CreditDeduction>> getByUserId(UUID userId) {
    return null;
  }

  @Override
  public Future<List<CreditDeduction>> getByAdminId(UUID adminId) {
    return null;
  }

}
