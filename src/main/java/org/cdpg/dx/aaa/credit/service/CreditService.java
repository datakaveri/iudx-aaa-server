package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.*;

import java.util.List;
import java.util.UUID;

public interface CreditService {

  // ************ CREDIT REQUEST *********
  Future<CreditRequest> createCreditRequest(UUID userId, double amount);

  Future<CreditRequest> getCreditRequestById(UUID id);

  Future<List<CreditRequest>> getAllRequestsByStatus(Status status);

  Future<Boolean> updateCreditRequestStatus(UUID requestId, Status status);

  // ************ CREDIT DEDUCTION *********

  Future<Boolean> logCreditDeduction(CreditDeductionDTO creditDeductionDTO);

  Future<List<CreditDeduction>> getDeductionsByUser(UUID userId);

  Future<List<CreditDeduction>> getDeductionsByAdmin(UUID adminId);

  // ************ USER CREDIT *********

  Future<UserCredit> getCreditByUserId(UUID userId);

  Future<Boolean> addCredits(UUID userId, double amount);

  Future<Boolean> deductCredits(UUID userId, double amount);

  Future<Boolean> updateCreditBalance(UserCreditDTO userCreditDTO);

  Future<UserCredit> getBalance(UUID userId);
}
