import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationJoinRequestDAOImpl {
  private final PostgresService postgresService;

  public OrganizationJoinRequestDAOImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }
}




