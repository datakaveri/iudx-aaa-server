package org.cdpg.dx.aaa.credit.dao;

//import org.cdpg.dx.aaa.credit.dao.impl.CreditDeductionDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.ComputeRoleDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.CreditTransactionDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.CreditRequestDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.UserCreditDAOImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class CreditDAOFactory {
  private final PostgresService postgresService;

  public CreditDAOFactory(PostgresService postgresService)
  {
    this.postgresService=postgresService;
  }

  public CreditRequestDAO creditRequestDAO() {
    return new CreditRequestDAOImpl(postgresService);
  }

  public CreditTransactionDAO creditTransactionDAO() {
    return new CreditTransactionDAOImpl(postgresService);
  }

  public ComputeRoleDAO computeRoleDAO() {
    return new ComputeRoleDAOImpl(postgresService);
  }
  public UserCreditDAO userCreditDAO() {
    return new UserCreditDAOImpl(postgresService) {
    };
  }
}
