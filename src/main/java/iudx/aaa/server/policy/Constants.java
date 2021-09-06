package iudx.aaa.server.policy;

public class Constants {

  public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";
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
  public static final String FAILURE = "failure";
  public static final String CATID = "catId";
  public static final String OWNERDETAILS = "ownerDetails";
  public static final String USERDETAILS = "userDetails";
  public static final String URL = "url";
  public static final String POLICYBY = "policyBy";
  public static final String POLICYFOR = "policyFor";
  public static final String DESCRIPTION = "description";
  public static final String INTERNALERROR = "internal server error";
  public static final String TYPE = "type";
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

  public static final String AUTH_SERVER_URL = "authdev.iudx.io";
  public static final String CAT_SERVER_URL = "catalogue.iudx.io";
  public static final String CAT_ITEM = "catalogue/crud";
  public static final String RESULTS = "results";
  public static final String CAT_ITEM_PATH = "/iudx/cat/v1/item";
  public static final String PROVIDER_ID = "provider_id";
  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

  // catalogue result
  public static final String IUDX_RES_GRP = "iudx:ResourceGroup";
  public static final String PROVIDER = "provider";
  public static final String RESOURCE_SERVER = "resourceServer";
  public static final String EMAIL_HASH = "email_hash";
  public static final String IUDX_RES = "iudx:Resource";
  public static final String RESOURCE_GROUP = "resourceGroup";
  public static final String RESOURCE_SERVER_ID = "resource_server_id";
  // failed messages
  public static final String ROLE_NOT_FOUND = "role not found";
  public static final String POLICY_NOT_FOUND = "policy not found";
  public static final String NOT_DELEGATE = "not a delegate";
  public static final String URL_NOT_FOUND = "url not found";
  public static final String AUTH_DEL_POL_FAIL = "Not an auth delegate";
  public static final String AUTH_DEL_FAIL = "Not a delegate for resource owner";
  public static final String ITEMNOTFOUND = "item does not exist:";
  public static final String NO_RES_SERVER = "Res server does not exist";
  // Title
  public static final String SUCC_TITLE_POLICY_READ = "policy read";
  public static final String SUCC_TITLE_POLICY_DEL = "policy deleted";
  public static final String SUCC_TITLE_LIST_DELEGS = "Delegations";
  public static final String SUCC_TITLE_DELETE_DELE = "Deleted requested delegations";
  public static final String DELETE_FAILURE = "Cannot delete policy";
  public static final String INVALID_ROLE = "invalid role to perform operation";
  public static final String INVALID_DELEGATE_POL = "user does not have access to auth server";
  public static final String INVALID_DELEGATE = "user does not have access to resource";
  public static final String ERR_TITLE_INVALID_ID = "Invalid delegation ID";
  public static final String ERR_TITLE_INVALID_ROLES = "User does not have roles to use API";
  public static final String ERR_TITLE_AUTH_DELE_DELETE = "Auth delegate may not delete auth delegations";
  public static final String ERR_DETAIL_DEL_DELEGATE_ROLES =
      "User with provider role or is an auth delegate may call the API";
  public static final String ERR_DETAIL_LIST_DELEGATE_ROLES =
      "User with provider/delegate role or is an auth delegate may call the API";
  // URN
  public static final String ID_NOT_PRESENT = "id does not exist";
  public static final String POLICY_SUCCESS = "urn:dx:as:Success";
  public static final String POLICY_FAILURE = "urn:dx:as:Failure";
  public static final String URN_INVALID_ROLE = "urn:dx:as:InvalidRole";
  public static final String URN_INVALID_DELEGATE = "urn:dx:as:InvalidDelegate";
  public static final String URN_INVALID_INPUT = "urn:dx:as:InvalidInput";
  // future failure messages
  public static final String BAD_REQUEST = "bad request:";
  public static final String SERVER_NOT_PRESENT = "servers not present:";
  public static final String VALIDATE_EXPIRY_FAIL = "expiry cannot be in the past:";
  public static final String INVALID_DATETIME = "invalid date time:";
  public static final String INVALID_USER = "user does not exist:";
  public static final String NO_AUTH_POLICY = "No auth policy for user:";
  public static final String UNAUTHORIZED = "Not allowed to create policies for resource:";
  public static final String DUPLICATE_POLICY = "Policy already exists:";
  public static final String NO_USER = "no user";
  public static final String NOT_RES_OWNER = "does not own the resource";
  public static final String NO_ADMIN_POLICY = "No admin policy";
  public static final String UNAUTHORIZED_DELEGATE = "Unauthorized";
  public static final String COMPOSE_FAILURE = "COMPOSE_FAILURE";

  // verify policy queries
  public static final String GET_FROM_ROLES_TABLE =
          "Select role from test.roles where user_id = $1::UUID "
                  + "AND role = $2::test.role_enum AND status = $3::test.role_status_enum";
  public static final String GET_RES_DETAIL = "select id,provider_id,resource_server_id from test.";
  public static final String GET_RES_DETAIL_JOIN = " where cat_id = $1::text ";

  public static final String GET_CONSUMER_CONSTRAINTS =
          "select constraints from test.policies where  user_id = $1::UUID "
                  + "and item_id = $2::UUID and item_type = $3::test.item_enum "
                  + "and status = $4::test.policy_status_enum and expiry_time > now()";

  public static final String GET_URL = "select url from test.resource_server where id = $1::UUID ";

  public static final String GET_RES_OWNER =
          "select id from test.users where email_hash = $1::text";

  public static final String GET_RES_SERVER_OWNER =
          "select id,url,owner_id from test.resource_server where url = $1::text";

  public static final String GET_RES_SER_OWNER =
          "select a.id,url,a.owner_id from test.resource_server a inner join test.";

  public static final String GET_RES_SER_OWNER_JOIN =
          " b on a.id = b.resource_server_id and b.cat_id = $1::text";

  public static final String CHECK_ADMIN_POLICY =
          "select id from test.policies where "
                  + " user_id = $1::UUID and owner_id = $2::UUID and item_id =$3::UUID "
                  + " and item_type = $4::test.item_enum and status = $5::test.policy_status_enum "
                  + " and expiry_time > now()";

  public static final String CHECK_DELEGATOINS_VERIFY ="select id from test.delegations where user_id = $1::UUID"
          + " and owner_id = $2::UUID and resource_server_id = $3::UUID "
          + " and status = $4::test.policy_status_enum";


  public static final String CHECK_POLICY = "select constraints from test.policies where user_id = $1::UUID"
          + " and owner_id = $2::UUID and item_id = $3::UUID and "
          + "status = $4::test.policy_status_enum "
          + " and expiry_time > now()";
  // List Policy queries
  public static final String GET_POLICIES =
          "Select a.id as policy_id, a.user_id, a.owner_id, a.item_id ,a.item_type ,"
                  + " a.expiry_time  ,a.constraints,b.cat_id  from test.policies a INNER JOIN test.";
  public static final String GET_POLICIES_JOIN =
          " b on a.item_id = b.id "
                  + "where a.owner_id = $1::UUID AND a.item_type = $2::test.item_enum  "
                  + "AND a.status = $3::test.policy_status_enum  AND a.expiry_time > NOW()";
  public static final String GET_POLICIES_JOIN_OWNER =
          " b on a.item_id = b.id "
                  + "where a.user_id = $1::UUID AND a.item_type = $2::test.item_enum  "
                  + "AND a.status = $3::test.policy_status_enum  AND a.expiry_time > NOW()";
  public static final String CHECK_RES_EXIST =
          "select id from test.policies"
                  + " where status = $1::test.policy_status_enum  and id = any($2::uuid[])";
  public static final String RES_OWNER_CHECK =
          "select id from test.policies where owner_id = $1::uuid "
                  + "and status = $2::test.policy_status_enum and id = any($3::uuid[]) and expiry_time > now()";
  public static final String DELEGATE_CHECK =
          "Select a.id from test.policies a "
                  + "INNER JOIN test.delegations b on a.owner_id = b.owner_id where "
                  + "a.user_id = $1::UUID and a.status =$2::test.policy_status_enum "
                  + " and a.expiry_time > now() and a.id = any($3::UUID[]) ";


  // delete policy queries
  public static final String CHECK_DELPOLICY =
          "select a.id from test.policies a inner join test.resource_server b on a.item_id = b.id "
                  + "where  a.user_id = $1::UUID and item_type = $2::test.item_enum "
                  + "and a.owner_id = b.owner_id and b.url = $3::varchar "
                  + "and a.status = $4::test.policy_status_enum and a.expiry_time > now()";
  public static final String DELETE_POLICY =
          "update test.policies set status = $1::test.policy_status_enum"
                  + " where status = $2::test.policy_status_enum and expiry_time > now() "
                  + " and id = any($3::uuid[])";
  // create policy
  public static final String CHECKUSEREXIST =
          "select id from test.users where id = any($1::uuid[])";
  public static final String CHECK_RES_SER =
          "select name as cat_id,id from test.resource_server "
                  + "where owner_id = $1::UUID and name = ANY($2::text[])";
  public static final String CHECKRESGRP =
          "select cat_id from test.resource_group where cat_id = ANY($1::text[]) ";
  public static final String CHECK_AUTH_POLICY =
          "select a.id from test.policies a inner join test.resource_server b "
                  + " on a.item_id = b.id where a.user_id = $1::UUID and "
                  + "  a.owner_id = b.owner_id and b.url = $2::varchar and "
                  + " a.status = $3::test.policy_status_enum and a.expiry_time > now() ";
  public static final String RES_GRP_OWNER =
          "select provider_id from test.resource_group where "
                  + "provider_id != $1::UUID  and cat_id = any($2::text[])";
  public static final String RES_OWNER =
          "select provider_id from test.resource where provider_id != $1::UUID"
                  + " and cat_id = any($2::text[])";
  public static final String CHECK_DELEGATION =
          "Select a.id from test.delegations a inner join test.resource_server b "
                  + " on a.resource_server_id = b.id where b.name = $1::text "
                  + " and a.user_id = $2::UUID and a.owner_id = any($3::UUID[]) ";


  public static final String GET_RES_GRP_OWNER =
          "select cat_id,provider_id from test.resource_group where cat_id = any($1::text[])";

  public static final String GET_RES_OWNERS =
          "select cat_id,provider_id from test.resource where cat_id = any($1::text[])";

  public static final String GET_RES_DETAILS =
          "select cat_id,id from test.resource where cat_id = ANY($1::text[]) ";

  public static final String GET_RES_SER_ID =
          "select url,id from test.resource_server where url = any($1::text[])";

  public static final String GET_PROVIDER_ID =
          "select email_hash,id from test.users where email_hash = any($1::text[])";

  public static final String INSERT_RES_GRP =
          "insert into test.resource_group "
                  + "(cat_id,provider_id,resource_server_id,created_at,updated_at) values"
                  + " ($1::text, $2::UUID, $3::UUID,now(),now()) "
                  + "on conflict (cat_id,provider_id,resource_server_id) do nothing";

  public static final String GET_RES_SER_DETAIL =
          "select id,provider_id,resource_server_id, "
                  + "cat_id from test.resource_group where cat_id = any($1::text[])";

  public static final String INSERT_RES =
          "insert into test.resource "
                  + "(cat_id,provider_id,resource_server_id,resource_group_id,created_at,updated_at) values"
                  + " ($1::text, $2::UUID, $3::UUID,$4::UUID,now(),now()) "
                  + "on conflict (cat_id,provider_id,resource_group_id) do nothing";

  public static final String INSERT_POLICY =
          "insert into test.policies (user_id,item_id,item_type,owner_id,status,"
                  + "expiry_time,constraints,created_at,updated_at) values"
                  + " ($1::UUID, $2::UUID, $3::test.item_enum, $4::UUID,$5::test.policy_status_enum "
                  + ", $6::timestamp without time zone, $7::jsonb, now() ,now()) ";

  public static final String CHECK_EXISTING_POLICY =
          "select id from test.policies where user_id =$1::UUID "
                  + " and item_id =$2::UUID and item_type = $3::test.item_enum and owner_id = $4::UUID "
                  + " and status = $5::test.policy_status_enum and expiry_time > now()";

  public static final String LIST_DELEGATE_AUTH_DELEGATE =
      "SELECT d.id, d.owner_id, d.user_id, url, name AS server "
          + "FROM test.delegations AS d JOIN test.resource_server ON"
          + " d.resource_server_id = resource_server.id WHERE d.owner_id = $1::uuid AND url != $2::text AND d.status = 'ACTIVE'";

  public static final String LIST_DELEGATE_AS_PROVIDER_DELEGATE =
      "SELECT d.id, d.owner_id, d.user_id, url, name AS server "
          + "FROM test.delegations AS d JOIN test.resource_server ON"
          + " d.resource_server_id = resource_server.id"
          + " WHERE d.status = 'ACTIVE' AND (d.owner_id = $1::uuid OR d.user_id = $1::uuid)";

  public static final String GET_DELEGATIONS_BY_ID =
      "SELECT d.id, url FROM test.delegations AS d JOIN test.resource_server ON"
          + " d.resource_server_id = resource_server.id"
          + " WHERE d.owner_id = $1::uuid AND d.id = ANY($2::uuid[]) AND d.status = 'ACTIVE'";

  public static final String DELETE_DELEGATIONS =
      "UPDATE test.delegations SET status = 'DELETED', updated_at = NOW()"
      + " WHERE owner_id = $1::uuid AND id = ANY($2::uuid[])";

  // item types
  public enum itemTypes {
    RESOURCE_SERVER("RESOURCE_SERVER"),
    RESOURCE_GROUP("RESOURCE_GROUP"),
    RESOURCE("RESOURCE");

    private String type;

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

    private String role;

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

    private String status;

    status(String status) {
      this.status = status;
    }

    public String getStatus() {
      return status;
    }
  }
}
