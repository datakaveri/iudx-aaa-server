package iudx.aaa.server.policy;

public class Constants {

    public static final String REGISTRATION_SERVICE_ADDRESS = "iudx.aaa.registration.service";

    //item types
    public enum itemTypes
    {
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

    //roles
    public enum roles
    {
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
    //status
    public enum status
    {
        APPROVED("APPROVED"),
        PENDING("PENDING"),
        ACTIVE("ACTIVE");

        private String status;

        status(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

    //db columns
    public static final String USERID                 = "userId";
    public static final String ITEMID                 = "itemId";
    public static final String ITEMTYPE               = "itemType";
    public static final String ROLE                   = "role";
    public static final String CONSTRAINTS            = "constraints";
    public static final String EXPIRYTIME             = "expiryTime";
    public static final String OWNERID                = "ownerId";
    public static final String RESOURCETABLE          = "resource";
    public static final String STATUS                 = "status";
    public static final String SUCCESS                = "success";
    public static final String FAILURE                = "failure";
    public static final String CATID                  = "catId";
    public static final String OWNERDETAILS           = "ownerDetails";
    public static final String USERDETAILS            = "userDetails";
    public static final String URL                    = "url";
    public static final String POLICYBY               = "policyBy";
    public static final String POLICYFOR              = "policyFor";
    public static final String DESCRIPTION            = "description";
    public static final String INTERNALERROR          = "internal server error";
    public static final String TYPE                   = "type";

    public static final String USER_ID                = "user_id";
    public static final String ITEM_ID                = "item_id";
    public static final String ITEM_TYPE              = "item_type";
    public static final String EXPIRY_TIME            = "expiry_time";
    public static final String OWNER_ID               = "owner_id";
    public static final String RESOURCE_GROUP_TABLE   = "resource_group";
    public static final String RESOURCE_TABLE         = "resource";
    public static final String CAT_ID                 = "cat_id";
    public static final String OWNER_DETAILS          = "owner_details";
    public static final String USER_DETAILS           = "user_details";
    public static final String POLICY_BY              = "policy_by";
    public static final String POLICY_FOR             = "policy_for";

    //failed messages
    public static final String ROLE_NOT_FOUND    = "role not found";
    public static final String POLICY_NOT_FOUND  = "policy not found";
    public static final String NOT_DELEGATE      = "not a delegate";
    public static final String URL_NOT_FOUND     = "url not found";

    public static final String SUCC_TITLE_POLICY_READ = "Policy read";
    public static final String POLICY_SUCCESS = "urn:dx:as:Success";




    //verify policy queries
    public static final String GET_FROM_ROLES_TABLE = "Select role from test.roles where user_id = $1::UUID " +
            "AND role = $2::test.role_enum AND status = $3::test.role_status_enum";

    public static final String GET_FROM_POLICY_TABLE = "Select constraints,owner_id from test.policies a " +
            "INNER JOIN test.";

    public static final String GET_FROM_POLICY_TABLE_JOIN = " b on a.item_id = b.id where " +
            "a.user_id = $1::UUID AND a.item_type = $2::test.item_enum" +
            " AND b.cat_id = $3::varchar AND a.expiry_time > NOW()  ";

    public static final String CHECK_DELEGATE = "select id from test.delegations where " +
            "owner_id = $1::UUID AND user_id = $2::UUID AND status = $3::test.policy_status_enum";

    public static final String GET_URL = "Select url from test.";

    public static final String GET_URL_JOIN = " INNER JOIN test.resource_server ON " +
            "resource_server.id = resource_server_id  WHERE cat_id = $1::varchar";

    //List Policy queries
    public static final String GET_POLICIES = "Select a.user_id,a.owner_id,a.item_id,a.item_type,a.expiry_time,a.constraints,b.cat_id " +
            "from test.policies a INNER JOIN test." ;

    public static final String GET_POLICIES_JOIN = " b on a.item_id = b.id " +
            "where a.owner_id = $1::UUID AND a.item_type = $2::test.item_enum  " +
            "AND a.status = $3::test.policy_status_enum  AND a.expiry_time > NOW()";


    public static final String GET_POLICIES_JOIN_OWNER = " b on a.item_id = b.id " +
            "where a.user_id = $1::UUID AND a.item_type = $2::test.item_enum  " +
            "AND a.status = $3::test.policy_status_enum  AND a.expiry_time > NOW()";
}
