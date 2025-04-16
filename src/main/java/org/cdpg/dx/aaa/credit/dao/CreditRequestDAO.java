package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.Status;

import java.util.List;
import java.util.UUID;

public interface CreditRequestDAO {
    Future<CreditRequest> create(UUID userId, double amount);

    Future<CreditRequest> getById(UUID requestId);

    Future<List<CreditRequest>> getAll(Status status);

    Future<Boolean> updateStatus(UUID requestId, Status status);
}
