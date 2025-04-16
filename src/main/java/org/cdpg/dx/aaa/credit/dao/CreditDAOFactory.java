package org.cdpg.dx.aaa.credit.dao;

import org.cdpg.dx.aaa.credit.dao.impl.CreditDeductionDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.CreditRequestDAOImpl;
import org.cdpg.dx.aaa.credit.dao.impl.UserCreditDAOImpl;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationDAOImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.glassfish.jaxb.core.v2.schemagen.episode.Package;

public class CreditDAOFactory {
  private final PostgresService postgresService;

  public CreditDAOFactory(PostgresService postgresService)
  {
    this.postgresService=postgresService;
  }

  public CreditRequestDAO creditRequestDAO() {
    return new CreditRequestDAOImpl(postgresService);
  }

  public CreditDeductionDAO creditDeductionDAO() {
    return new CreditDeductionDAOImpl(postgresService);
  }

  public UserCreditDAO userCreditDAO() {
    return new UserCreditDAOImpl(postgresService) {
    };
  }
}
