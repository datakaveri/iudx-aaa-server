package org.cdpg.dx.aaa.organization.dao;

import io.vertx.core.Future;
import org.apache.maven.model.Organization;
import org.cdpg.dx.aaa.organization.models.UpdateOrgDTO;

import java.util.List;
import java.util.UUID;

public interface OrganizationDAO {
  //Future<Organization> getByName(String name);
  Future<Organization> update(UUID id, UpdateOrgDTO updateOrgDTO);

  Future<Boolean> delete(UUID orgId);

  Future<List<Organization>> getAll();

  Future<Organization> get(UUID orgId);



}
