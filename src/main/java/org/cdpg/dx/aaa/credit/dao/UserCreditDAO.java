package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;

import java.util.UUID;

public interface UserCreditDAO {

  Future<UserCredit> getById(UUID userId);

  Future<Boolean> add(UUID userId, double amount);

  Future<Boolean> deduct(UUID userId, double amount);

  Future<Boolean> update(UserCreditDTO userCreditDTO);

  Future<UserCredit> get(UUID userId);
}
