package iudx.aaa.server.policy;

public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  // db columns
  public static final String USERID = "userId";
  public static final String ITEMID = "itemId";
  public static final String ITEMTYPE = "itemType";
  public static final String ROLE = "role";
  public static final String CONSTRAINTS = "constraints";
  public static final String EXPIRYTIME = "expiryTime";
  public static final String OWNERID = "ownerId";
  public static final String RESOURCETABLE = "resource";
  public static final String STATUS = "status";
  public static final String SUCCESS = "success";
  public static final String URL = "url";
  public static final String INTERNALERROR = "Internal server error";
  public static final String TYPE = "type";
  public static final String TITLE = "title";
  public static final String ID = "id";
  public static final String RES_SERVER = "resServer";
  public static final String RES_GRP = "resGrp";
  public static final String RES = "res";
  public static final String USER_ID = "user_id";
  public static final String ITEM_ID = "item_id";
  public static final String ITEM_TYPE = "item_type";
  public static final String EXPIRY_TIME = "expiry_time";
  public static final String OWNER_ID = "owner_id";
  public static final String RESOURCE_GROUP_TABLE = "resource_group";
  public static final String RESOURCE_TABLE = "resource";
  public static final String CAT_ID = "cat_id";
  public static final String OWNER_DETAILS = "owner";
  public static final String USER_DETAILS = "user";
  public static final String CONSUMER_ROLE = "CONSUMER";
  public static final String PROVIDER_ROLE = "PROVIDER";
  public static final String DELEGATE_ROLE = "DELEGATE";


  public static final String RESULTS = "results";
  public static final String CAT_ITEM_PATH = "/iudx/cat/v1/item";
  public static final String PROVIDER_ID = "provider_id";
  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

  // catalogue result
  public static final String IUDX_RES_GRP = "iudx:ResourceGroup";
  public static final String PROVIDER = "provider";
  public static final String RESOURCE_SERVER = "resourceServer";
  public static final String RESOURCE_SERVER_TABLE = "resource_server";
  public static final String EMAIL_HASH = "email_hash";
  public static final String IUDX_RES = "iudx:Resource";
  public static final String RESOURCE_GROUP = "resourceGroup";
  public static final String RESOURCE_SERVER_ID = "resource_server_id";
  public static final String RESOURCE_GROUP_ID = "resource_group_id";
  // failed messages
  public static final String ROLE_NOT_FOUND = "role not found";
  public static final String POLICY_NOT_FOUND = "policy not found";
  public static final String NOT_DELEGATE = "user is not a delegate:";
  public static final String URL_NOT_FOUND = "url not found";
  public static final String AUTH_DEL_POL_FAIL = "Not an auth delegate";
  public static final String AUTH_DEL_FAIL = "Not a delegate for resource owner";
  public static final String ITEMNOTFOUND = "Item does not exist";
  public static final String NO_RES_SERVER = "Res server does not exist";
  public static final String DUPLICATE = "Request must be unique";
  // Title
  public static final String SUCC_TITLE_POLICY_READ = "policy read";
  public static final String SUCC_TITLE_POLICY_DEL = "policy deleted";
  public static final String SUCC_TITLE_LIST_DELEGS = "Delegations";
  public static final String SUCC_TITLE_DELETE_DELE = "Deleted requested delegations";
  public static final String DELETE_FAILURE = "Cannot delete policy";
  public static final String INVALID_ROLE = "Invalid role to perform operation";
  public static final String INVALID_DELEGATE_POL = "user does not have access to auth server";
  public static final String INVALID_DELEGATE = "user does not have access to resource";
  public static final String ERR_TITLE_INVALID_ID = "Invalid delegation ID";
  public static final String ERR_TITLE_INVALID_ROLES = "User does not have roles to use API";
  public static final String ERR_TITLE_AUTH_DELE_DELETE =
      "Auth delegate may not delete auth delegations";
  public static final String ERR_DETAIL_DEL_DELEGATE_ROLES =
      "User with provider role or is an auth delegate may call the API";
  public static final String ERR_DETAIL_LIST_DELEGATE_ROLES =
      "User with provider/delegate role or is an auth delegate may call the API";
  public static final String ERR_TITLE_AUTH_DELE_CREATE =
      "Auth delegate may not create auth delegations";
  // URN
  public static final String ID_NOT_PRESENT = "id does not exist";

  public static final String CAT_SUCCESS_URN = "urn:dx:cat:Success";
  // future failure messages
  public static final String BAD_REQUEST = "bad request";
  public static final String SERVER_NOT_PRESENT = "servers not present:";
  public static final String VALIDATE_EXPIRY_FAIL = "expiry cannot be in the past:";
  public static final String INVALID_DATETIME = "invalid date time:";
  public static final String INVALID_USER = "user does not exist";
  public static final String NO_AUTH_POLICY = "No auth policy for user";
  public static final String INCORRECT_ITEM_TYPE = "incorrect item type";
  public static final String UNAUTHORIZED = "Not allowed to create policies for resource";
  public static final String PROVIDER_NOT_REGISTERED = "Provider not a registered user";
  public static final String DUPLICATE_POLICY = "Policy already exists:";
  public static final String DUPLICATE_DELEGATION = "Delegation already exists:";
  public static final String NO_USER = "no user";
  public static final String NOT_RES_OWNER = "does not own the resource";
  public static final String NO_ADMIN_POLICY = "No admin policy";
  public static final String UNAUTHORIZED_DELEGATE = "Unauthorized";
  public static final String COMPOSE_FAILURE = "COMPOSE_FAILURE";

  public static final String LOG_DB_ERROR = "Fail: Databse query; ";
  public static final String ERR_DUP_NOTIF_REQ = "Fail: Duplicate Access notification request; ";
  public static final String DUP_NOTIF_REQ = "Access request already exists";
  public static final String INVALID_TUPLE = "Unable to map request to db tuple";
  public static final String SUCC_NOTIF_REQ = "Notification access request";
  public static final String SUCC_LIST_NOTIF_REQ = "Access requests";
  public static final String ERR_LIST_NOTIF = "Fail: Unable to list notification access requests";
  public static final String SUCC_UPDATE_NOTIF_REQ = "Request updated";
  public static final String REQ_ID_ALREADY_NOT_EXISTS = "requestId not exists";
  public static final String REQ_ID_ALREADY_PROCESSED = "requestId already processed";

  // verify policy queries
  public static final String GET_FROM_ROLES_TABLE =
      "Select role from  roles where user_id = $1::UUID "
          + "AND role = $2::role_enum AND status = $3::role_status_enum";

  public static final String GET_CONSUMER_CONSTRAINTS =
      "select constraints from  policies where  user_id = $1::UUID "
          + "and item_id = $2::UUID and item_type = $3::item_enum "
          + "and status = $4::policy_status_enum and expiry_time > now()";

  public static final String GET_URL = "select url from resource_server where id = $1::UUID ";

  public static final String GET_RES_OWNER = "select id from users where email_hash = $1::text";

  public static final String GET_RES_SERVER_OWNER =
      "select id,url,owner_id from resource_server where url = $1::text";

  public static final String GET_RES_SERVER_OWNER_ID =
      "select id,url,owner_id from resource_server where id = $1::UUID";

  public static final String GET_RES_SER_OWNER =
      "select a.id,a.url,a.owner_id from resource_server a inner join ";

  public static final String GET_RES_SER_OWNER_JOIN =
      " b on a.id = b.resource_server_id where b.cat_id = $1::text";

  public static final String CHECK_ADMIN_POLICY =
      "select id from policies where "
          + " user_id = $1::UUID and owner_id = $2::UUID and item_id =$3::UUID "
          + " and item_type = $4::item_enum and status = $5::policy_status_enum "
          + " and expiry_time > now()";

  public static final String CHECK_DELEGATOINS_VERIFY =
      "select id from delegations where user_id = $1::UUID"
          + " and owner_id = $2::UUID and resource_server_id = $3::UUID "
          + " and status = $4::policy_status_enum";

  public static final String CHECK_POLICY =
      "select constraints from policies where user_id = $1::UUID"
          + " and owner_id = $2::UUID and item_id = $3::UUID and "
          + "status = $4::policy_status_enum "
          + " and expiry_time > now()";
  // List Policy queries
  public static final String GET_POLICIES =
      "Select a.id as  \"policyId\", a.user_id, a.owner_id ,a.item_type as \"itemType\" ,"
          + " a.expiry_time as \"expiryTime\" ,a.constraints, b.cat_id  as \"itemId\" from policies a INNER JOIN ";
  public static final String GET_SERVER_POLICIES =
      "Select a.id as  \"policyId\", a.user_id, a.owner_id ,a.item_type as \"itemType\" ,"
          + " a.expiry_time as \"expiryTime\" ,a.constraints, b.url  as \"itemId\" from policies a INNER JOIN ";
  public static final String GET_POLICIES_JOIN =
      " b on a.item_id = b.id "
          + "where  a.item_type = $2::item_enum  "
          + "AND a.status = $3::policy_status_enum  AND a.expiry_time > NOW() And (a.owner_id = $1::UUID or  a.user_id = $1::UUID)";
  public static final String GET_POLICIES_JOIN_DELEGATE =
      " b on a.item_id = b.id "
          + "where  a.item_type = $2::item_enum  "
          + "AND a.status = $3::policy_status_enum  AND a.expiry_time > NOW() And  a.owner_id = $1::UUID";

  public static final String CHECK_POLICY_EXIST =
      "select id,owner_id,item_type from policies"
          + " where status = $1::policy_status_enum  and id = any($2::uuid[])";
  public static final String RES_OWNER_CHECK =
      "select id from policies where owner_id = $1::uuid "
          + "and status = $2::policy_status_enum and id = any($3::uuid[]) and expiry_time > now()";
  public static final String DELEGATE_CHECK =
      "select a.id from policies a inner join delegations b "
          + " on a.owner_id = b.owner_id inner join resource_server c "
          + " on b.resource_server_id = c.id where b.user_id = $1::UUID and "
          + "b.status =$2::policy_status_enum and c.url = $3::text and a.id = any($4::uuid[]) ";

  // delete policy queries
  public static final String CHECK_DELPOLICY =
      "select a.id from policies a inner join resource_server b on a.item_id = b.id "
          + "where  a.user_id = $1::UUID and item_type = $2::item_enum "
          + "and a.owner_id = b.owner_id and b.url = $3::varchar "
          + "and a.status = $4::policy_status_enum and a.expiry_time > now()";
  public static final String DELETE_POLICY =
      "update policies set status = $1::policy_status_enum , updated_at = now()"
          + " where status = $2::policy_status_enum and expiry_time > now() "
          + " and id = any($3::uuid[])";
  // create policy

  public static final String CHECKUSEREXIST = "select id from users where id = any($1::uuid[])";
  public static final String CHECK_RES_SER =
      "select id, url as cat_id,owner_id as owner_id from resource_server "
          + "where owner_id = $1::UUID and url = ANY($2::text[])";


  public static final String CHECK_RESOURCE_EXIST = "select cat_id from ";
  public static final String CHECK_RESOURCE_EXIST_JOIN = " where cat_id = ANY($1::text[]) ";
  public static final String CHECK_AUTH_POLICY =
      "select a.id from policies a inner join resource_server b "
          + " on a.item_id = b.id where a.user_id = $1::UUID and "
          + "  a.owner_id = b.owner_id and b.url = $2::varchar and "
          + " a.status = $3::policy_status_enum and a.expiry_time > now() ";

  public static final String GET_RES_GRP_DETAILS =
      "select id,cat_id ,provider_id as owner_id, resource_server_id from resource_group where cat_id = ANY($1::text[]) ";

  public static final String GET_RES_DETAILS =
      "select id,cat_id ,provider_id as owner_id,resource_group_id, resource_server_id from resource where cat_id = ANY($1::text[]) ";

  public static final String GET_RES_SER_ID =
      "select url,id from resource_server where url = any($1::text[])";

  public static final String GET_PROVIDER_ID =
      "select email_hash,id from users where email_hash = any($1::text[])";

  public static final String INSERT_RES_GRP =
      "insert into resource_group "
          + "(cat_id,provider_id,resource_server_id,created_at,updated_at) values"
          + " ($1::text, $2::UUID, $3::UUID,now(),now()) "
          + "on conflict (cat_id,provider_id,resource_server_id) do nothing";

  public static final String GET_RES_SER_DETAIL =
      "select id,provider_id,resource_server_id, "
          + "cat_id from resource_group where cat_id = any($1::text[])";

  public static final String INSERT_RES =
      "insert into resource "
          + "(cat_id,provider_id,resource_server_id,resource_group_id,created_at,updated_at) values"
          + " ($1::text, $2::UUID, $3::UUID,$4::UUID,now(),now()) "
          + "on conflict (cat_id,provider_id,resource_group_id) do nothing";

  public static final String CHECK_DELEGATION =
      "Select a.owner_id from delegations a inner join resource_server b "
          + " on a.resource_server_id = b.id where b.url = $1::text "
          + " and a.user_id = $2::UUID and a.status = $3::policy_status_enum"
          + " and a.owner_id = any($4::UUID[])";

  public static final String INSERT_POLICY =
      "insert into policies (user_id,item_id,item_type,owner_id,status,"
          + "expiry_time,constraints,created_at,updated_at) values"
          + " ($1::UUID, $2::UUID, $3::item_enum, $4::UUID,$5::policy_status_enum "
          + ", $6::timestamp without time zone, $7::jsonb, now() ,now()) ";

  public static final String CHECK_EXISTING_POLICY =
      "select id from policies where user_id =$1::UUID "
          + " and item_id =$2::UUID and item_type = $3::item_enum and owner_id = $4::UUID "
          + " and status = $5::policy_status_enum and expiry_time > now()";

  public static final String LIST_DELEGATE_AUTH_DELEGATE =
      "SELECT d.id, d.owner_id, d.user_id, url, name AS server "
          + "FROM delegations AS d JOIN resource_server ON"
          + " d.resource_server_id = resource_server.id WHERE d.owner_id = $1::uuid AND url != $2::text AND d.status = 'ACTIVE'";

  public static final String LIST_DELEGATE_AS_PROVIDER_DELEGATE =
      "SELECT d.id, d.owner_id, d.user_id, url, name AS server "
          + "FROM delegations AS d JOIN resource_server ON"
          + " d.resource_server_id = resource_server.id"
          + " WHERE d.status = 'ACTIVE' AND (d.owner_id = $1::uuid OR d.user_id = $1::uuid)";

  public static final String GET_DELEGATIONS_BY_ID =
      "SELECT d.id, url FROM delegations AS d JOIN resource_server ON"
          + " d.resource_server_id = resource_server.id"
          + " WHERE d.owner_id = $1::uuid AND d.id = ANY($2::uuid[]) AND d.status = 'ACTIVE'";

  public static final String DELETE_DELEGATIONS =
      "UPDATE delegations SET status = 'DELETED', updated_at = NOW()"
          + " WHERE owner_id = $1::uuid AND id = ANY($2::uuid[])";

  public static final String CREATE_NOTIFI_POLICY_REQUEST =
      "INSERT INTO access_requests (user_id, item_id,item_type, owner_id, status, "
          + "expiry_duration, constraints, created_at, updated_at)\n"
          + "VALUES ($1::UUID, $2::UUID, $3::item_enum,$4::UUID,$5::acc_reqs_status_enum,"
          + "$6::interval,$7::jsonb, now() ,now()) RETURNING id as \"requestId\";";

  public static final String SELECT_NOTIF_POLICY_REQUEST =
      "SELECT id FROM access_requests WHERE user_id = $1::UUID AND "
          + "item_id = $2::UUID AND owner_id = $3::UUID AND status = $4::acc_reqs_status_enum";

  public static final String SELECT_PROVIDER_NOTIF_REQ =
      "SELECT id as \"requestId\", user_id, item_id as \"itemId\", lower(item_type::text) as \"itemType\", owner_id, lower(status::text) AS status, "
          + "expiry_duration::text as \"expiryDuration\", constraints FROM "
          + "access_requests WHERE owner_id = $1::UUID";

  public static final String SELECT_CONSUM_NOTIF_REQ =
      "SELECT id as \"requestId\", user_id, item_id as \"itemId\", lower(item_type::text) as \"itemType\", owner_id, lower(status::text) AS status, "
          + "expiry_duration::text as \"expiryDuration\", constraints FROM "
          + "access_requests WHERE user_id = $1::UUID";

  public static final String SEL_NOTIF_REQ_ID =
      "SELECT id, user_id as \"userId\", item_id as \"itemId\", item_type as \"itemType\", owner_id as \"ownerId\", "
          + "status, expiry_duration::text as \"expiryDuration\", "
          + "constraints FROM access_requests where id = ANY($1::UUID[])";

  public static final String SEL_NOTIF_ITEM_ID =
      "SELECT * FROM (SELECT id, cat_id AS url FROM resource\n"
          + "UNION SELECT id, url AS url FROM resource_server\n"
          + "UNION select id, cat_id AS url FROM resource_group) view WHERE id = ANY($1::UUID[])";

  public static final String UPDATE_NOTIF_REQ_APPROVED =
      "UPDATE access_requests SET status = $1::acc_reqs_status_enum, expiry_duration = $2::interval, "
          + "constraints =$3::jsonb, updated_at = NOW() WHERE id = $4::UUID and status = 'PENDING'";

  public static final String UPDATE_NOTIF_REQ_REJECTED =
      "UPDATE access_requests SET status = $2::acc_reqs_status_enum, updated_at = NOW() WHERE id = $1::UUID and status = 'PENDING'";

  public static final String SEL_NOTIF_POLICY_ID =
      "SELECT ar.id AS \"requestId\", pol.id AS \"policyId\" FROM "
          + "access_requests ar "
          + "LEFT JOIN policies pol ON ar.user_id = pol.user_id AND ar.item_id = pol.item_id AND ar.owner_id = pol.owner_id \n"
          + "WHERE pol.user_id= $1::UUID AND pol.item_id = $2::UUID AND ar.owner_id = $3::UUID AND pol.status = 'ACTIVE'";

  public static final String INSERT_NOTIF_APPROVED_ID =
      "INSERT INTO approved_access_requests(request_id,policy_id,created_at,updated_at) "
          + "VALUES ($1::UUID,$2::UUID, NOW(),NOW());";

  public static final String SET_INTERVALSTYLE = "SET LOCAL intervalstyle = 'iso_8601'";

  public static final String INSERT_DELEGATION =
      "insert into delegations (owner_id,user_id,resource_server_id,status,created_at,updated_at) values "
          + " ($1::UUID, $2::UUID, $3::UUID, $4::policy_status_enum, now() ,now())";

  // create delegation

  public static final String CHECK_ROLES =
      "select user_id from roles where role = $1::role_enum"
          + " and status = $2::role_status_enum and user_id = ANY($3::UUID[]) ";

  public static final String GET_SERVER_DETAILS =
      "select url,id from resource_server where url =  ANY($1::text[])";

  public static final String CHECK_AUTH_POLICY_DELEGATION =
      "select * from policies a inner join resource_server b on a.item_id = b.id "
          + " where a.user_id = $1::UUID and a.item_type = $2::item_enum and"
          + " b.url = $3::text and a.status =$4::policy_status_enum and a.expiry_time > now() ";

  public static final String CHECK_EXISTING_DELEGATIONS =
      "select id from delegations where owner_id = $1::UUID "
          + " and user_id =$2::UUID and resource_server_id = $3::UUID and  "
          + " status = $4::policy_status_enum ";

  // item types
  public enum itemTypes {
    RESOURCE_SERVER("RESOURCE_SERVER"),
    RESOURCE_GROUP("RESOURCE_GROUP"),
    RESOURCE("RESOURCE");

    private final String type;

    itemTypes(String item) {
      this.type = item;
    }

    public String getUrl() {
      return type;
    }
  }

  // roles
  public enum roles {
    ADMIN("ADMIN"),
    PROVIDER("PROVIDER"),
    CONSUMER("CONSUMER"),
    DELEGATE("DELEGATE");

    private final String role;

    roles(String role) {
      this.role = role;
    }

    public String getRole() {
      return role;
    }
  }

  // status
  public enum status {
    APPROVED("APPROVED"),
    PENDING("PENDING"),
    ACTIVE("ACTIVE"),
    DELETED("DELETED"),
    SUCCESS("SUCCESS");

    private final String status;

    status(String status) {
      this.status = status;
    }

    public String getStatus() {
      return status;
    }
  }
}
