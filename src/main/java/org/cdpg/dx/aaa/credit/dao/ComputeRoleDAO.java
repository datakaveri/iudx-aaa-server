package org.cdpg.dx.aaa.credit.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.Status;

import java.util.List;
import java.util.UUID;

public interface ComputeRoleDAO {
  Future<ComputeRole> create(ComputeRole computeRole);

  Future<List<ComputeRole>> getAll(Status status);

  Future<Boolean> updateStatus(UUID requestId, Status status,UUID approvedBy);
}
