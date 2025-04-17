package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.dao.CreditDeductionDAO;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.organization.dao.*;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class CreditServiceImpl implements CreditService{

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationServiceImpl.class);

  private final CreditRequestDAO creditRequestDAO;
  private final UserCreditDAO userCreditDAO;
  private final CreditDeductionDAO creditDeductionDAO;

  public CreditServiceImpl(CreditDAOFactory factory) {
    this.creditRequestDAO = factory.creditRequestDAO();
    this.userCreditDAO = factory.userCreditDAO();
    this.creditDeductionDAO = factory.creditDeductionDAO();
  }

  @Override
  public Future<CreditRequest> createCreditRequest(UUID userId, double amount) {
    return creditRequestDAO.create(userId,amount);
  }

  @Override
  public Future<CreditRequest> getCreditRequestById(UUID id) {
    return creditRequestDAO.getById(id);
  }

  @Override
  public Future<List<CreditRequest>> getAllRequestsByStatus(Status status) {
    return creditRequestDAO.getAll(status);
  }

  @Override
  public Future<Boolean> updateCreditRequestStatus(UUID requestId, Status status) {
    return creditRequestDAO.updateStatus(requestId,status);
  }

  @Override
  public Future<Boolean> logCreditDeduction(CreditDeductionDTO creditDeductionDTO) {
    return creditDeductionDAO.log(creditDeductionDTO);
  }

  @Override
  public Future<List<CreditDeduction>> getDeductionsByUser(UUID userId) {
    return creditDeductionDAO.getByUserId(userId);
  }

  @Override
  public Future<List<CreditDeduction>> getDeductionsByAdmin(UUID adminId) {
    return creditDeductionDAO.getByAdminId(adminId);
  }

  @Override
  public Future<UserCredit> getCreditByUserId(UUID userId) {
    return userCreditDAO.getById(userId);
  }

  @Override
  public Future<Boolean> addCredits(UUID userId, double amount) {
    return userCreditDAO.add(userId,amount);
  }

  @Override
  public Future<Boolean> deductCredits(UUID userId, double amount) {
    return userCreditDAO.deduct(userId,amount);
  }

  @Override
  public Future<Boolean> updateCreditBalance(UserCreditDTO userCreditDTO) {
    return userCreditDAO.update(userCreditDTO);
  }

  @Override
  public Future<UserCredit> getBalance(UUID userId) {
    return userCreditDAO.get(userId);
  }
}
