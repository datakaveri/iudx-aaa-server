package iudx.aaa.server.policy;

public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  public static final String APD_SERVICE_ADDRESS = "iudx.aaa.apd.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  public static final int DB_RECONNECT_ATTEMPTS = 5;
  public static final long DB_RECONNECT_INTERVAL_MS = 10000;
  
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
  public static final String APD = "APD";
  public static final String RES = "res";
  public static final String USER_ID = "user_id";
  public static final String ITEM_ID = "item_id";
  public static final String APD_ID = "apd_id";
  public static final String USER_CLASS = "user_class";
  public static final String ITEM_TYPE = "item_type";
  public static final String EXPIRY_TIME = "expiry_time";
  public static final String OWNER_ID = "owner_id";
  public static final String RESOURCE_GROUP_TABLE = "resource_group";
  public static final String RESOURCE_TABLE = "resource";
  public static final String CAT_ID = "cat_id";
  public static final String APD_DETAILS = "apd";
  public static final String OWNER_DETAILS = "owner";
  public static final String USER_DETAILS = "user";
  public static final String CONSUMER_ROLE = "CONSUMER";
  public static final String PROVIDER_ROLE = "PROVIDER";
  public static final String DELEGATE_ROLE = "DELEGATE";
  public static final String USR_POL = "USER";
  public static final String APD_POL = "APD";

  public static final String CALL_APD_APDID = "apdId";
  public static final String CALL_APD_USERID = "userId";
  public static final String CALL_APD_ITEM_ID = "itemId";
  public static final String CALL_APD_ITEM_TYPE = "itemType";
  public static final String CALL_APD_RES_SER_URL = "resSerUrl";
  public static final String CALL_APD_USERCLASS = "userClass";
  public static final String CALL_APD_OWNERID = "ownerId";
  public static final String CALL_APD_CONSTRAINTS = "constraints";

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
  public static final String POLICY_NOT_FOUND = "policy not found";
  public static final String NOT_DELEGATE = "user is not a delegate";
  public static final String ITEMNOTFOUND = "Item does not exist";
  public static final String NO_RES_SERVER = "Res server does not exist";
  public static final String DUPLICATE = "Request must be unique";
  // Title
  public static final String SUCC_TITLE_POLICY_READ = "policy read";
  public static final String SUCC_TITLE_POLICY_DEL = "policy deleted";
  public static final String SUCC_TITLE_LIST_DELEGS = "Delegations";
  public static final String SUCC_TITLE_DELETE_DELE = "Deleted requested delegations";
  public static final String INVALID_ROLE = "Invalid role to perform operation";
  public static final String INVALID_INPUT = "Invalid Input";
  public static final String INVALID_DELEGATE = "user does not have access to resource";
  public static final String ERR_TITLE_INVALID_ID = "Invalid delegation ID";
  public static final String ERR_TITLE_INVALID_ROLES = "User does not have roles to use API";
  public static final String ERR_TITLE_AUTH_DELE_DELETE =
      "Auth delegate may not delete auth delegations";
  public static final String ERR_DETAIL_DEL_DELEGATE_ROLES =
      "User with provider role or is an auth delegate may call the API";
  public static final String ERR_DETAIL_LIST_DELEGATE_ROLES =
      "User with provider/delegate role or is an auth delegate may call the API";
  public static final String ERR_DETAIL_CONSUMER_ROLES =
      "User with consumer role may call the API";
  public static final String ERR_TITLE_AUTH_DELE_CREATE =
      "Auth delegate may not create auth delegations";
  // URN
  public static final String ID_NOT_PRESENT = "id does not exist";

  public static final String CAT_SUCCESS_URN = "urn:dx:cat:Success";
  // future failure messages
  public static final String BAD_REQUEST = "bad request";
  public static final String SERVER_NOT_PRESENT = "servers not present";
  public static final String VALIDATE_EXPIRY_FAIL = "expiry cannot be in the past:";
  public static final String INVALID_DATETIME = "invalid date time:";
  public static final String INVALID_USER = "user does not exist";
  public static final String INVALID_APD_STATUS = "APD not in active state";
  public static final String NO_AUTH_POLICY = "No auth policy for user";
  public static final String NO_AUTH_TRUSTEE_POLICY = "No auth policy for user by trustee";
  public static final String INCORRECT_ITEM_TYPE = "incorrect item type";
  public static final String INCORRECT_ITEM_ID = "incorrect item ID";
  public static final String UNAUTHORIZED = "Not allowed to create policies for resource";
  public static final String PROVIDER_NOT_REGISTERED = "Provider not a registered user";
  public static final String DUPLICATE_POLICY = "Policy already exists:";
  public static final String DUPLICATE_DELEGATION = "Delegation already exists";
  public static final String NO_USER = "no user";
  public static final String NOT_RES_OWNER = "does not own the resource";
  public static final String NO_ADMIN_POLICY = "No admin policy";
  public static final String UNAUTHORIZED_DELEGATE = "Unauthorized";

  public static final String LOG_DB_ERROR = "Fail: Databse query; ";
  public static final String ERR_DUP_NOTIF_REQ = "Fail: Duplicate Access notification request; ";
  public static final String DUP_NOTIF_REQ = "Access request already exists";
  public static final String INVALID_TUPLE = "Unable to map request to db tuple";
  public static final String SUCC_NOTIF_REQ = "Notification access request";
  public static final String SUCC_LIST_NOTIF_REQ = "Access requests";
  public static final String DELETE_NOTIF_REQ = "deleted requests";
  public static final String ERR_LIST_NOTIF = "Fail: Unable to list notification access requests";
  public static final String ERR_DELETE_NOTIF = "Fail: Unable to delete notification access requests";
  public static final String SUCC_UPDATE_NOTIF_REQ = "Request updated";
  public static final String REQ_ID_ALREADY_NOT_EXISTS = "requestId does not exists";
  public static final String REQ_ID_ALREADY_PROCESSED = "requestId already processed";

  // verify policy queries
  public static final String GET_FROM_ROLES_TABLE =
      "Select role from  roles where user_id = $1::UUID "
          + "AND role = $2::role_enum AND status = $3::role_status_enum";

  public static final String GET_CONSUMER_USER_POL_CONSTRAINTS =
      "select constraints from  policies where  user_id = $1::UUID "
          + "and item_id = $2::UUID and item_type = $3::item_enum "
          + "and status = $4::policy_status_enum and expiry_time > now()";

  public static final String GET_CONSUMER_APD_POL_DETAILS =
      "select apd_id, constraints, user_class from apd_policies where"
          + " item_id = $1::UUID and item_type = $2::item_enum"
          + " and status = $3::policy_status_enum";

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

  public static final String EXISTING_ACTIVE_USR_POL =
      "SELECT id FROM policies WHERE owner_id = $1::uuid AND status = 'ACTIVE' AND id = ANY($2::uuid[]) ";

  public static final String EXISTING_ACTIVE_USR_POL_NO_RES_SER =
      "SELECT id FROM policies WHERE owner_id = $1::uuid AND status = 'ACTIVE'"
      + " AND id = ANY($2::uuid[]) AND item_type != 'RESOURCE_SERVER'";

  public static final String EXISTING_ACTIVE_APD_POL =
      "SELECT id FROM apd_policies WHERE owner_id = $1::uuid AND status = 'ACTIVE' AND id = ANY($2::uuid[]) ";

  public static final String GET_USER_POLICIES_AUTH_DELEGATE =
      "SELECT id AS \"policyId\", user_id, owner_id, item_id, item_type AS \"itemType\","
          + " expiry_time AS \"expiryTime\", constraints FROM policies WHERE status = 'ACTIVE'"
          + " AND owner_id = $1::uuid";
  
  public static final String GET_USER_POLICIES =
      "SELECT id AS \"policyId\", user_id, owner_id, item_id, item_type AS \"itemType\","
          + " expiry_time AS \"expiryTime\", constraints FROM policies WHERE status = 'ACTIVE'"
          + " AND (owner_id = $1::uuid OR user_id = $1::uuid)";
  
  public static final String GET_APD_POLICIES =
      "SELECT id AS \"policyId\", apd_id, user_class AS \"userClass\", owner_id, item_id,"
      + " item_type AS \"itemType\", expiry_time AS \"expiryTime\", constraints FROM apd_policies "
          + "WHERE status = 'ACTIVE' AND owner_id = $1::uuid";


  // delete policy queries


  public static final String DELETE_USR_POLICY =
"UPDATE policies SET status = 'DELETED', updated_at = NOW() WHERE id = ANY($1::uuid[])";

  public static final String DELETE_APD_POLICY =
"UPDATE apd_policies SET status = 'DELETED', updated_at = NOW() WHERE id = ANY($1::uuid[])";
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

  public static final String CHECK_TRUSTEE_POLICY =
          "select id from policies where user_id = $1::UUID and status = $2::policy_status_enum " +
                  "and expiry_time > now() and  item_id = ANY($3::UUID[])";

  public static final String GET_RES_GRP_DETAILS =
      "select id,cat_id ,provider_id as owner_id, resource_server_id from resource_group where cat_id = ANY($1::text[]) ";

  public static final String GET_RES_DETAILS =
      "select id,cat_id ,provider_id as owner_id,resource_group_id, resource_server_id from resource where cat_id = ANY($1::text[]) ";

  public static final String GET_RES_SER_ID =
      "select url,id from resource_server where url = any($1::text[])";

  public static final String GET_RES_GRP_CAT_IDS =
      "SELECT id, cat_id FROM resource_group WHERE id = ANY($1::uuid[]) ";

  public static final String GET_RES_CAT_IDS =
      "SELECT id, cat_id FROM resource WHERE id = ANY($1::uuid[]) ";

  public static final String GET_RES_SER_URLS =
      "SELECT url, id FROM resource_server WHERE id = ANY($1::uuid[])";

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


  public static final String INSERT_POLICY =
      "insert into policies (user_id,item_id,item_type,owner_id,status,"
          + "expiry_time,constraints,created_at,updated_at) values"
          + " ($1::UUID, $2::UUID, $3::item_enum, $4::UUID,$5::policy_status_enum "
          + ", $6::timestamp without time zone, $7::jsonb, now() ,now()) ";

  public static final String INSERT_APD_POLICY =
          "insert into apd_policies (apd_id,user_class,item_id,item_type,owner_id,status,"
                  + "expiry_time,constraints,created_at,updated_at) values"
                  + " ($1::UUID,$2::text,$3::UUID, $4::item_enum, $5::UUID,$6::policy_status_enum "
                  + ", $7::timestamp without time zone, $8::jsonb, now() ,now()) ";


  public static final String CHECK_EXISTING_POLICY =
      "select id from policies where user_id =$1::UUID "
          + " and item_id =$2::UUID and item_type = $3::item_enum and owner_id = $4::UUID "
          + " and status = $5::policy_status_enum and expiry_time > now()";

  public static final String CHECK_EXISTING_APD_POLICY =
          "select id from apd_policies where item_id =$1::UUID and item_type = $2::item_enum and owner_id = $3::UUID "
                  + " and status = $4::policy_status_enum and expiry_time > now()";

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
          + "$6::interval,$7::jsonb, now() ,now()) RETURNING id as \"requestId\"";

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
          + "constraints FROM access_requests where id = ANY($1::UUID[]) AND owner_id = $2::UUID";

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
          + "WHERE pol.user_id= $1::UUID AND pol.item_id = $2::UUID AND ar.owner_id = $3::UUID AND ar.id = $4::UUID"
          + " AND pol.status = 'ACTIVE'";

  public static final String INSERT_NOTIF_APPROVED_ID =
      "INSERT INTO approved_access_requests(request_id,policy_id,created_at,updated_at) "
          + "VALUES ($1::UUID,$2::UUID, NOW(),NOW())";

  public static final String SET_INTERVALSTYLE = "SET LOCAL intervalstyle = 'iso_8601'";

  public static final String GET_NOTIFICATIONS_BY_ID_CONSUMER = "SELECT id as \"requestId\", "
      + "user_id, item_id as \"itemId\", lower(item_type::text) as \"itemType\","
      + " owner_id, lower(status::text) AS status, expiry_duration::text as \"expiryDuration\","
      + " constraints FROM access_requests  WHERE user_id = $1::uuid AND id = ANY($2::uuid[]) "
      + " AND status = 'PENDING'";

  public static final String DELETE_NOTIFICATIONS =
      "UPDATE access_requests SET status = 'WITHDRAWN', updated_at = NOW()"
          + " WHERE user_id = $1::uuid AND id = ANY($2::uuid[])";

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
    RESOURCE("RESOURCE"),
    APD("APD");

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
    SUCCESS("SUCCESS"),
    WITHDRAWN("WITHDRAWN");

    private final String status;

    status(String status) {
      this.status = status;
    }

    public String getStatus() {
      return status;
    }
  }
}
