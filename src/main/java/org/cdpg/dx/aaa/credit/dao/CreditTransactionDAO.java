package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;

import java.util.UUID;

public interface CreditTransactionDAO {

  Future<Boolean> logTransaction(CreditTransaction creditTransaction);
}
