package iudx.aaa.server.apd;

public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
  public static final String POLICY_SERVICE_ADDRESS = "iudx.aaa.policy.service";
  public static final String TOKEN_SERVICE_ADDRESS = "iudx.aaa.token.service";

  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  public static final String UUID_REGEX =
          "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  /* Config related */
  public static final String CONFIG_AUTH_URL = "authServerDomain";
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_SCHEMA = "databaseSchema";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String DATABASE_POOLSIZE = "poolSize";
  public static final int DB_CONNECT_TIMEOUT = 10000;

  /* Response fields */
  public static final String RESP_APD_ID = "apdId";
  public static final String RESP_APD_NAME = "name";
  public static final String RESP_APD_URL = "url";
  public static final String RESP_APD_STATUS = "status";
  public static final String RESP_APD_OWNER = "owner";
  public static final String RESP_OWNER_USER_ID = "id";
  public static final String INTERNALERROR = "internal server error";

  /* Response title and details */
  public static final String SUCC_TITLE_REGISTERED_APD =
      "The Access Policy Domain has been registered";

  public static final String SUCC_TITLE_UPDATED_APD =
      "The status of the Access Policy Domains have been updated";

  public static final String SUCC_TITLE_APD_READ = "Apd read successfully";

  public static final String ERR_TITLE_NO_USER_PROFILE = "User profile does not exist";
  public static final String ERR_DETAIL_NO_USER_PROFILE = "Please register to create user profile";

  public static final String ERR_TITLE_NOT_TRUSTEE = "Not a trustee";
  public static final String ERR_DETAIL_NOT_TRUSTEE = "Use does not have the trustee role";

  public static final String ERR_TITLE_APD_NOT_RESPOND = "Invalid APD response";
  public static final String ERR_DETAIL_APD_NOT_RESPOND =
      "The APD is not responsive/has not responded correctly";

  public static final String ERR_TITLE_INVALID_DOMAIN = "Invalid URL";
  public static final String ERR_DETAIL_INVALID_DOMAIN = "The URL is invalid";

  public static final String ERR_TITLE_EXISTING_DOMAIN = "URL already exists";
  public static final String ERR_DETAIL_EXISTING_DOMAIN =
      "An APD with the requested URL already exists";

  public static final String ERR_TITLE_DUPLICATE_REQ = "Duplicate APD ID in request";

  public static final String ERR_TITLE_INVALID_APDID = "Invalid APD ID";

  public static final String ERR_TITLE_CANT_CHANGE_APD_STATUS =
      "Not allowed to change status for APD ID";

  public static final String ERR_TITLE_NO_ROLES_PUT = "Invalid roles to call API";
  public static final String ERR_DETAIL_NO_ROLES_PUT =
      "Trustees and Auth Server Admin may call the API";

  public static final String ERR_TITLE_INVALID_REQUEST =  "Invalid request";


  public static final String ERR_DETAIL_INVALID_UUID = "Invalid UUID";

  /* SQL */
  public static final String SQL_INSERT_APD_IF_NOT_EXISTS =
      "INSERT INTO apds (name, url, owner_id, status, created_at, updated_at) VALUES "
          + "($1::text, $2::text, $3::uuid, 'PENDING', NOW(), NOW()) "
          + "ON CONFLICT (url) DO NOTHING RETURNING id";

  public static final String SQL_CHECK_ADMIN_OF_SERVER =
      "SELECT id FROM " + "resource_server WHERE owner_id = $1::uuid AND url = $2::text";

  public static final String SQL_GET_APDS_BY_ID_ADMIN =
      "SELECT id AS \"apdId\", name, url, owner_id, status FROM apds WHERE id = ANY($1::uuid[])";

  public static final String SQL_GET_APDS_BY_ID_TRUSTEE =
      "SELECT id AS \"apdId\", name, url, owner_id, status FROM apds WHERE id = ANY($1::uuid[]) AND owner_id = $2::uuid";

  public static final String SQL_UPDATE_APD_STATUS =
      "UPDATE apds SET status = $1::apd_status_enum, updated_at = NOW() WHERE id = $2::uuid";
  
  public static final String SQL_GET_APD_URL_STATUS =
      "SELECT url, status FROM apds WHERE id = $1::uuid";

  /* APD API endpoints and request metadata */
  public static final String APD_READ_USERCLASSES_API = "/userclasses";
  public static final String APD_VERIFY_API = "/verify";
  public static final String APD_VERIFY_AUTH_HEADER = "Authorization";
  public static final String APD_VERIFY_BEARER = "Bearer ";
  
  /* Allowed APD URNs */
  public static final String APD_URN_ALLOW = "urn:apd:Allow";
  public static final String APD_URN_DENY = "urn:apd:Deny";
  public static final String APD_URN_DENY_NEEDS_INT = "urn:apd:DenyNeedsInteraction";
  public static final String APD_URN_REGEX =
      "^(" + APD_URN_ALLOW + "|" + APD_URN_DENY + "|" + APD_URN_DENY_NEEDS_INT + ")$";
  
  /* APD JSON request keys */
  public static final String APD_REQ_USER = "user";
  public static final String APD_REQ_PROVIDER = "provider";
  public static final String APD_REQ_RESOURCE =  "resource";
  public static final String APD_REQ_USERCLASS = "userClass";
  
  /* APD JSON response keys */
  public static final String APD_RESP_TYPE = "type";
  public static final String APD_RESP_TITLE = "title";
  public static final String APD_RESP_DETAIL = "detail";
  public static final String APD_RESP_SESSIONID = "sessionId";
  public static final String APD_RESP_LINK = "link";
  
  /* create token service JSON key/values */
  public static final String CREATE_TOKEN_URL = "url";
  public static final String CREATE_TOKEN_CONSTRAINTS = "constraints";
  public static final String CREATE_TOKEN_CAT_ID = "cat_id";
  public static final String CREATE_TOKEN_SESSIONID = "sessionId";
  public static final String CREATE_TOKEN_LINK = "link";
  public static final String CREATE_TOKEN_STATUS = "status";
  public static final String CREATE_TOKEN_SUCCESS = "success";
  public static final String CREATE_TOKEN_APD_INTERAC = "apd-interaction";
  
  public static final String APD_NOT_ACTIVE = " (NOTE: The APD is currently not in an active state.)";
  public static final String ERR_TITLE_POLICY_EVAL_FAILED = "Policy evaluation failed";

  public static final String GET_APDINFO_ID = "SELECT id,name,url,status,owner_id as \"ownerId\" FROM apds where id = ANY($1::uuid[])";
  public static final String GET_APDINFO_URL = "SELECT id,name,url,status,owner_id as \"ownerId\" FROM apds where url = ANY($1::text[])";
  public static final String LIST_AUTH_QUERY = "SELECT id FROM apds where status = $1::apd_status_enum or status = $2::apd_status_enum or status = $3::apd_status_enum";
  public static final String LIST_TRUSTEE_QUERY = "SELECT id FROM apds WHERE (owner_id = $1::UUID OR status = $2::apd_status_enum) ";
  public static final String LIST_USER_QUERY = "SELECT id FROM apds WHERE status = $1::apd_status_enum  ";
}
