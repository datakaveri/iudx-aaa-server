package iudx.aaa.server.policy;

/** Constants and SQL queries for {@link PolicyService}. */
public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  public static final String APD_SERVICE_ADDRESS = "iudx.aaa.apd.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  public static final int DB_RECONNECT_ATTEMPTS = 5;
  public static final long DB_RECONNECT_INTERVAL_MS = 10000;

  public static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  // db columns
  public static final String STATUS = "status";
  public static final String SUCCESS = "success";
  public static final String URL = "url";
  public static final String INTERNALERROR = "Internal server error";
  public static final String CAT_ID = "cat_id";
  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String CREATE_TOKEN_DID = "delegatorUserId";
  public static final String CREATE_TOKEN_DRL = "delegatorRole";
  public static final String CREATE_TOKEN_RG = "resourceGroupId";

  /* Catalogue related constants */

  public static final String CALL_APD_APDURL = "apdUrl";
  public static final String CALL_APD_USERID = "userId";
  public static final String CALL_APD_ITEM_ID = "itemId";
  public static final String CALL_APD_ITEM_TYPE = "itemType";
  public static final String CALL_APD_RES_SER_URL = "resSerUrl";
  public static final String CALL_APD_OWNERID = "ownerId";
  public static final String CALL_APD_CONTEXT = "context";

  public static final String RESULTS = "results";

  // failed messages

  public static final String ITEMNOTFOUND = "Item does not exist";
  public static final String ERR_NOT_VALID_RESOURCE = "Requested item is not a valid resource";
  public static final String NO_RES_SERVER = "Res server does not exist";
  // Title
  public static final String SUCC_TITLE_LIST_DELEGS = "Delegations";
  public static final String SUCC_TITLE_DELETE_DELE = "Deleted requested delegations";
  public static final String SUCC_TITLE_DELEG_EMAILS = "Delegate emails";

  public static final String RESP_DELEG_EMAILS = "delegateEmails";

  public static final String INVALID_ROLE = "Invalid role to perform operation";
  public static final String INVALID_INPUT = "Invalid Input";

  public static final String ERR_TITLE_INVALID_ID = "Invalid delegation ID";
  public static final String ERR_TITLE_INVALID_ROLES = "User does not have roles to use API";
  public static final String ERR_DETAIL_DEL_DELEGATE_ROLES =
      "User with provider or consumer role may call the API";
  public static final String ERR_DETAIL_LIST_DELEGATE_ROLES =
      "User with provider, consumer, delegate role may call the API";
  public static final String ERR_DETAIL_CREATE_DELEGATE_ROLES =
      "User with provider/consumer role may call the API";
  public static final String ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE =
      "User does not have role for requested RS URL / RS does not exist ";
  public static final String ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE =
      "User does not have requested delegated role for given resource server"
          + " (Resource server may not exist also)";
  public static final String ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE = "rsUrls";

  // future failure messages
  public static final String INCORRECT_ITEM_TYPE = "incorrect item type";
  public static final String INCORRECT_ITEM_ID = "Item ID is not a valid UUID";
  public static final String DUPLICATE_DELEGATION = "Delegation already exists";
  public static final String NOT_RES_OWNER = "Provider does not own the resource";

  public static final String ERR_DETAIL_PROVIDER_DOESNT_HAVE_RS_ROLE =
      "Provider does not have role for resource server hosting the item";
  public static final String ERR_DETAIL_DELEGATED_RS_URL_NOT_MATCH_ITEM_RS =
      "The resource server associated with the supplied delegation ID"
          + " does not match the resource server hosting the item";
  public static final String ERR_DETAIL_CONSUMER_DOESNT_HAVE_RS_ROLE =
      "Provider does not have role for resource server hosting the item";
  public static final String ERR_DETAIL_PROVIDER_CANNOT_ACCESS_PII_RES =
      "Requested resource is a PII resource - provider cannot access directly";

  public static final String LIST_DELEGATION_AS_DELEGATOR_OR_DELEGATE =
      "SELECT d.id, d.user_id, url, roles.user_id AS delegator_id, lower(roles.role::text) AS role, name AS server "
          + "FROM delegations AS d JOIN roles ON roles.id = d.role_id"
          + " JOIN resource_server ON"
          + " roles.resource_server_id = resource_server.id"
          + " WHERE d.status = 'ACTIVE' AND roles.status = 'APPROVED'"
          + " AND (roles.user_id = $1::uuid OR d.user_id = $1::uuid)";

  public static final String GET_DELEGATIONS_BY_ID =
      "SELECT d.id FROM delegations AS d"
          + " JOIN roles ON roles.id = d.role_id"
          + " JOIN resource_server ON"
          + " roles.resource_server_id = resource_server.id"
          + " WHERE roles.user_id = $1::uuid AND d.id = ANY($2::uuid[]) AND d.status = 'ACTIVE'";

  public static final String DELETE_DELEGATIONS =
      "UPDATE delegations SET status = 'DELETED', updated_at = NOW()"
          + " WHERE id = ANY($1::uuid[])";

  public static final String INSERT_DELEGATION =
      "insert into delegations (user_id, role_id, status, created_at, updated_at) values "
          + " ($1::UUID, $2::UUID, $3::policy_status_enum, now() ,now())";

  // for unit testing
  public static final String TEST_INSERT_DELEGATION =
      "insert into delegations (id, user_id, role_id, status, created_at, updated_at) values "
          + " ($1::UUID, $2::UUID, $3::UUID, $4::policy_status_enum, now() ,now())";

  public static final String GET_ROLE_IDS_BY_ROLE_AND_RS =
      "SELECT roles.id, url, role FROM roles"
          + " JOIN resource_server ON roles.resource_server_id = resource_server.id"
          + " WHERE roles.user_id = $1::UUID AND role = ANY($2::role_enum[]) AND url = ANY($3::text[])";

  public static final String CHECK_EXISTING_DELEGATIONS =
      "select id from delegations where user_id = $1::UUID "
          + " and role_id = $2::UUID and status = $3::policy_status_enum ";

  // roles.user_id is the owner of the role therefore the delegator
  public static final String SQL_GET_DELEG_USER_IDS_BY_DELEGATION_INFO =
      "SELECT delegations.user_id FROM delegations"
          + " JOIN roles ON delegations.role_id = roles.id"
          + " JOIN resource_server on roles.resource_server_id = resource_server.id"
          + " WHERE roles.user_id = $1::uuid AND roles.role = $2::role_enum"
          + " AND resource_server.url = $3::text"
          + " AND delegations.status = 'ACTIVE' AND roles.status = 'APPROVED'";
}
