package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.*;

import java.util.List;
import java.util.UUID;

public interface CreditService {

  // ************ CREDIT REQUEST *********
  Future<CreditRequest> createCreditRequest(CreditRequest creditRequest); // done -> creates a new entry in credit request table

  Future<List<CreditRequest>> getAllPendingRequests(); //pending

  Future<Boolean> updateCreditRequestStatus(UUID requestId, Status status,UUID transactedBy); //done -> on approval will create a new entry in userCredit table

  // ************ USER CREDIT *********

  Future<Boolean> deductCredits(CreditTransaction creditTransaction);

  Future<Double> getBalance(UUID userId);

  // ************ COMPUTE ROLE **********

  Future<ComputeRole> create(ComputeRole computeRole);

  Future<List<ComputeRole>> getAll();

  Future<Boolean> updateStatus(UUID requestId, Status status,UUID approvedBy);

}
