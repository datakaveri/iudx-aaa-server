package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditRequestDTO;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.models.UserCreditDTO;

import java.util.List;
import java.util.UUID;

public interface CreditRequestDAO {
    Future<CreditRequest> create(CreditRequest creditRequest);

    Future<List<CreditRequest>> getAll(Status status);

    Future<Boolean> updateStatus(UUID requestId, Status status);

    Future<CreditRequest> getCreditRequestById(UUID requestId);
}
