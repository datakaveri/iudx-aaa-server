package iudx.aaa.server.policy;

public class Constants {

    //item types
    public enum itemTypes
    {
        RESOURCE_GROUP("RESOURCE_GROUP"),
        RESOURCE_ID("RESOURCE");

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

    public static final String USER_ID                = "userId";
    public static final String ITEM_ID                = "itemId";
    public static final String ITEM_TYPE              = "itemType";
    public static final String ROLE                   = "role";
    public static final String OWNER_ID               = "owner_id";
    public static final String RESOURCE_GROUP_TABLE   = "resource_group";
    public static final String RESOURCE_TABLE         = "resource";
    public static final String STATUS                 = "status";
    public static final String SUCCESS                = "success";
    public static final String CAT_ID                 = "cat_id";
    public static final String URL                    = "url";
    //failed messages
    public static final String ROLE_NOT_FOUND    = "role not found";
    public static final String POLICY_NOT_FOUND  = "policy not found";
    public static final String NOT_DELEGATE      = "not a delegate";
    public static final String URL_NOT_FOUND     = "url not found";

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
}
