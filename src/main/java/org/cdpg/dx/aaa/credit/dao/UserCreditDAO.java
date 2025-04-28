package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;

import java.util.UUID;

public interface UserCreditDAO {



  Future<Double> getBalance(UUID userId);

  Future<Boolean> updateBalance(UUID userId, double updatedAmount);
}
