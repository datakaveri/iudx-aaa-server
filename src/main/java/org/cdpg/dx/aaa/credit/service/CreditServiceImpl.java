package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.dao.*;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationServiceImpl;
import org.postgresql.core.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CreditServiceImpl implements CreditService {

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationServiceImpl.class);

  private final CreditRequestDAO creditRequestDAO;
  private final UserCreditDAO userCreditDAO;
  private final CreditTransactionDAO creditTransactionDAO;
  private final ComputeRoleDAO computeRoleDAO;


  public CreditServiceImpl(CreditDAOFactory factory) {
    this.creditRequestDAO = factory.creditRequestDAO();
    this.userCreditDAO = factory.userCreditDAO();
    this.creditTransactionDAO = factory.creditTransactionDAO();
    this.computeRoleDAO = factory.computeRoleDAO();
  }

  @Override
  public Future<CreditRequest> createCreditRequest(CreditRequest creditRequest) {
    return creditRequestDAO.create(creditRequest);
  }


  @Override
  public Future<List<CreditRequest>> getAllPendingRequests() {
    return creditRequestDAO.getAll(Status.PENDING);
  }


  // ***************************************************************************************
  @Override
  public Future<Boolean> updateCreditRequestStatus(UUID requestId, Status status,UUID transactedBy) {

    return creditRequestDAO.updateStatus(requestId, status)
      .compose(approved -> {
        if (!approved) return Future.succeededFuture(false);
        if (Status.APPROVED.getStatus().equals(status.getStatus())) {
          return creditRequestDAO.getCreditRequestById(requestId)
            .compose(cr ->{
             UUID userId = cr.userId();
             double amount = cr.amount();

             return getBalance(userId)
               .compose(balance-> userCreditDAO.updateBalance(userId,balance+amount))
               .compose(log->{

                 CreditTransaction creditTransaction = new CreditTransaction(
                   Optional.empty(),
                   userId,
                   amount,
                   transactedBy,
                   Optional.of(TransactionStatus.SUCCESS.getStatus()),
                   Optional.of(TransactionType.CREDIT.getType()),
                   Optional.empty());

                  return creditTransactionDAO.logTransaction(creditTransaction);
               })
               .map(true);
            });
          } else {
            return Future.succeededFuture(false);
          }
      });
  }


  //***************************************************************************************
  @Override
  public Future<Double> getBalance(UUID userId) {
    return userCreditDAO.getBalance(userId);
  }


  @Override
  public Future<Boolean> deductCredits(CreditTransaction creditTransaction) {
    UUID userId = creditTransaction.userId();
    double amount = creditTransaction.amount();

    return getBalance(userId)
      .compose(balance -> {
        if (balance < amount) {
          return Future.succeededFuture(false);
        } else {
          return userCreditDAO.updateBalance(userId, balance - amount)
            .compose(v -> {
              CreditTransaction completeTransaction = new CreditTransaction(
                Optional.empty(),
                userId,
                amount,
                creditTransaction.transactedBy(),
                Optional.of(TransactionStatus.SUCCESS.getStatus()),
                Optional.of(TransactionType.DEBIT.getType()),                                  // transactionType (or "CREDIT" if adding)
                Optional.of(Instant.now().toString())     // createdAt - current time
              );

              return creditTransactionDAO.logTransaction(completeTransaction);
            })
            .map(true);
        }
      });
  }


  @Override
  public Future<ComputeRole> create(ComputeRole computeRole) {
    return computeRoleDAO.create(computeRole);
  }

  @Override
  public Future<List<ComputeRole>> getAll() {
    return computeRoleDAO.getAll(Status.PENDING);
  }

  @Override
  public Future<Boolean> updateStatus(UUID requestId, Status status,UUID approvedBy) {
    return computeRoleDAO.updateStatus(requestId,status,approvedBy);
  }

}
