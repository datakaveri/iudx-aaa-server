package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditDeduction;
import org.cdpg.dx.aaa.credit.models.CreditDeductionDTO;

import java.util.List;
import java.util.UUID;

public interface CreditDeductionDAO {
    Future<Boolean> log(CreditDeductionDTO creditDeductionDTO);

    Future<List<CreditDeduction>> getByUserId(UUID userId);

    Future<List<CreditDeduction>> getByAdminId(UUID adminId);
}
