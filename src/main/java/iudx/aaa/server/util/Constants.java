package iudx.aaa.server.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

public class Constants {


  /** Service Addresses. */
  public static final String PG_SERVICE_ADDRESS = "iudx.aaa.pgsql.service";
  public static final String ORGANIZATION_SERVICE_ADDRESS = "iudx.aaa.organization.service";


  /** General. */

  public static final String CONFIG_FILE = "config.properties";
  public static final String OPTIONAL_MODULES = "optionalModules";
  public static final String IS_SSL = "ssl";
  public static final String PORT = "httpPort";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTORE_PASSWORD = "keystorePassword";
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_UNAME = "databaseUser";
  public static final String DOC_INDEX = "docIndex";
  public static final String RATING_INDEX = "ratingIndex";
  public static final String MLAYER_INSTANCE_INDEX = "mlayerInstanceIndex";
  public static final String MLAYER_DOMAIN_INDEX = "mlayerDomainIndex";

  public static final String PUBLIC_KEY = "publicKey";
  public static final String DATABASE_PASSWD = "databasePassword";
  public static final String SOURCE = "_source";
  public static final String KEYWORD_KEY = ".keyword";

  public static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}$");




  /** Item type. */
  public static final String RELATIONSHIP = "relationship";
  public static final String RESOURCE = "resource";
  public static final String RESOURCE_GRP = "resourceGroup";
  public static final String RESOURCE_SVR = "resourceServer";
  public static final String PROVIDER = "provider";
  public static final String PROVIDERS = "providers";
  public static final String ALL = "all";
  public static final String COS = "cos";
  public static final String OWNER = "owner";
  public static final String COS_ADMIN = "cos_admin";
  public static final String PROVIDER_USER_ID = "ownerUserId";
  public static final String RESOURCE_SERVER_URL = "resourceServerRegURL";
  public static final String COS_ITEM = "cos";
  public static final String RESOURCETYPE = "resourceType";



  /** Item types. */
  public static final String ITEM_TYPE_RESOURCE = "iudx:Resource";
  public static final String ITEM_TYPE_RESOURCE_GROUP = "iudx:ResourceGroup";
  public static final String ITEM_TYPE_RESOURCE_SERVER = "iudx:ResourceServer";
  public static final String ITEM_TYPE_PROVIDER = "iudx:Provider";
  public static final String ITEM_TYPE_COS = "iudx:COS";
  public static final String ITEM_TYPE_OWNER = "iudx:Owner";
  public static final String ITEM_TYPE_INSTANCE = "iudx:Instance";

  public static final ArrayList<String> ITEM_TYPES =
      new ArrayList<String>(Arrays.asList(ITEM_TYPE_RESOURCE, ITEM_TYPE_RESOURCE_GROUP,
          ITEM_TYPE_RESOURCE_SERVER, ITEM_TYPE_PROVIDER, ITEM_TYPE_COS, ITEM_TYPE_OWNER));

  public static final String AGGREGATIONS = "aggregations";
  public static final String INSTANCE = "instance";
  public static final String BUCKETS = "buckets";
  public static final String ID = "id";
  public static final String ITEM = "item";

  public static final String RESOURCE_ID = "resourceId";

  public static final String ITEM_TYPE = "itemType";

  public static final String PROPERTY = "property";
  public static final String VALUE = "value";

  /** GeoRels. */
  public static final String GEOREL_WITHIN = "within";
  public static final String GEOREL_NEAR = "near";
  public static final String GEOREL_COVERED_BY = "coveredBy";
  public static final String GEOREL_INTERSECTS = "intersects";
  public static final String GEOREL_EQUALS = "equals";
  public static final String GEOREL_DISJOINT = "disjoint";

  /** Geometries. */
  public static final String BBOX = "bbox";
  public static final String GEOMETRY = "geometry";
  public static final String GEOPROPERTY = "geoproperty";
  public static final String GEORELATION = "georel";
  public static final String INTERSECTS = "intersects";
  public static final String LINESTRING = "LineString";
  public static final String LOCATION = "location";
  public static final String MAX_DISTANCE = "maxDistance";
  public static final String POINT = "Point";
  public static final String POLYGON = "Polygon";
  public static final String COORDINATES = "coordinates";
  public static final String Q_VALUE = "q";
  public static final String LIMIT = "limit";
  public static final String OFFSET = "offset";

  /** SearchTypes. */
  public static final String SEARCH_TYPE = "searchType";
  public static final String SEARCH_TYPE_GEO = "geoSearch_";
  public static final String SEARCH_TYPE_TEXT = "textSearch_";
  public static final String SEARCH_TYPE_ATTRIBUTE = "attributeSearch_";
  public static final String SEARCH_TYPE_TAGS = "tagsSearch_";
  public static final String RESPONSE_FILTER = "responseFilter_";

  public static final String MESSAGE = "detail";
  public static final String RESULTS = "results";
  public static final String METHOD = "method";
  public static final String HTTP_METHOD = "httpMethod";
  public static final String STATUS = "title";
  public static final String TITLE = "title";
  public static final String TYPE = "type";
  public static final String DETAIL = "detail";
  public static final String FAILED = "failed";
  public static final String ERROR = "error";
  public static final String DESC = "detail";

  /** DB Query. */
  public static final String TOTAL_HITS = "totalHits";
  public static final String INCLUDE_FIELDS = "includeFields";
  public static final String FIELD = "field";
  public static final String QUERY_KEY = "query";
  public static final String HITS = "hits";
  public static final String TOTAL = "total";
  public static final String TERM = "term";
  public static final String NAME = "name";
  public static final String FILTER = "filter";
  public static final String TAGS = "tags";
  public static final String AVERAGE_RATING = "average_rating";
  public static final String TOTAL_RATINGS = "totalRatings";
  public static final String ICON_BASE64 = "icon_base64";
  public static final String PROVIDER_DES = "providerDescription";
  public static final String RESOURCE_COUNT = "resourceCount";
  public static final String PROVIDER_COUNT = "providerCount";
  public static final String RESOURCE_GROUP_COUNT = "resourceGroupCount";

  /** HTTP Methods. */
  public static final String REQUEST_GET = "GET";
  public static final String REQUEST_POST = "POST";
  public static final String REQUEST_PUT = "PUT";
  public static final String REQUEST_PATCH = "PATCH";
  public static final String REQUEST_DELETE = "DELETE";

  /** Error Messages. */
  public static final String DATABASE_ERROR = "DB Error. Check logs for more information";

  /** Operation type. */
  public static final String INSERT = "insert";
  public static final String UPDATE = "update";
  public static final String DELETE = "delete";

  /** Limits/Constraints. */
  public static final long COORDINATES_SIZE = 10;
  public static final int COORDINATES_PRECISION = 6;
  public static final int STRING_SIZE = 100;
  public static final int PROPERTY_SIZE = 4;
  public static final int VALUE_SIZE = 4;
  public static final int FILTER_VALUE_SIZE = 10;
  public static final int ID_SIZE = 512;
  public static final int INSTANCE_SIZE = 100;
  public static final int FILTER_PAGINATION_SIZE = 10000;
  public static final int OFFSET_PAGINATION_SIZE = 9999;
  public static final int MAX_RESULT_WINDOW = 10000;
  public static final int MAXDISTANCE_LIMIT = 10000; // 10KM
  public static final int SERVICE_TIMEOUT = 3000;

  public static final int POPULAR_DATASET_COUNT = 6;
  public static final String FILTER_PAGINATION_FROM = "0";
  public static final String MAX_LIMIT = "10000";


  public static final String SUCCESS = "Success";

  /* URN Codes */
  public static final String TYPE_WRONG_PROVIDER = "urn:dx:cat:WrongProvider";
  public static final String TYPE_WRONG_RESOURCESERVER = "urn:dx:cat:WrongResourceServer";
  public static final String TYPE_WRONG_RESOURCEGROUP = "urn:dx:cat:WrongResourceGroup";
  public static final String TYPE_INVALID_SCHEMA = "urn:dx:cat:InvalidSchema";
  public static final String TYPE_ID_NONEXISTANT = "urn:dx:cat:IdNonExistant";
  public static final String TYPE_ALREADY_EXISTS = "urn:dx:cat:AlreadyExists";
  public static final String TYPE_SUCCESS = "urn:dx:cat:Success";
  public static final String TYPE_FAIL = "urn:dx:cat:Fail";
  public static final String TYPE_ACCESS_DENIED = "urn:dx:cat:AccessDenied";
  public static final String TYPE_TOKEN_INVALID = "urn:dx:cat:InvalidAuthorizationToken";
  public static final String TYPE_MISSING_TOKEN = "urn:dx:cat:MissingAuthorizationToken";
  public static final String TYPE_ITEM_NOT_FOUND = "urn:dx:cat:ItemNotFound";
  public static final String TYPE_INVALID_SYNTAX = "urn:dx:cat:InvalidSyntax";
  public static final String TYPE_MISSING_PARAMS = "urn:dx:cat:MissingParams";
  public static final String TYPE_INTERNAL_SERVER_ERROR = "urn:dx:cat:InternalError";
  public static final String TYPE_OPERATION_NOT_ALLOWED = "urn:dx:cat:OperationNotAllowed";
  public static final String TYPE_LINK_VALIDATION_FAILED = "urn:dx:cat:LinkValidationFailed";
  public static final String TYPE_DB_ERROR = "urn:dx:cat:DatabaseError";
  public static final String TYPE_CONFLICT = "urn:dx:cat:Conflicts";

  public static final String TITLE_WRONG_PROVIDER = "Wrong Provider";
  public static final String TITLE_WRONG_RESOURCESERVER = "Wrong Resource Server";
  public static final String TITLE_WRONG_RESOURCEGROUP = "Wrong Resource Group";
  public static final String TITLE_INVALID_SCHEMA = "Invalid Schema";
  public static final String TITLE_ID_NONEXISTANT = "ID doesn't exist";
  public static final String TITLE_ALREADY_EXISTS = "Item already exists";
  public static final String TITLE_SUCCESS = "Success";
  public static final String TITLE_TOKEN_INVALID = "Token is invalid";
  public static final String TITLE_MISSING_TOKEN = "Token is missing";
  public static final String TITLE_ITEM_NOT_FOUND = "Item is not found";
  public static final String TITLE_INVALID_SYNTAX = "Invalid Syntax";
  public static final String TITLE_MISSING_PARAMS = "Missing parameters";

  public static final String TITLE_INTERNAL_SERVER_ERROR = "Internal error";
  public static final String TITLE_OPERATION_NOT_ALLOWED = "Operation not allowed";
  public static final String TITLE_LINK_VALIDATION_FAILED = "Link Validation Failed";
  public static final String TITLE_REQUIREMENTS_NOT_MET =
          "Resource usage requirements not satisfied";

  public static final String DETAIL_CONFLICT = "Conflicts";
  public static final String DETAIL_INTERNAL_SERVER_ERROR = "Internal error";
  public static final String DETAIL_WRONG_ITEM_TYPE = "Wrong Item Type";
  public static final String DETAIL_ID_NOT_FOUND = "id not present in the request";
  public static final String DETAIL_ITEM_NOT_FOUND = "Item not found";

  public static final String TYPE_INVALID_GEO_PARAM = "urn:dx:cat:InvalidGeoParam";
  public static final String TITLE_INVALID_GEO_PARAM = "Geoquery parameter error";

  public static final String TYPE_INVALID_GEO_VALUE = "urn:dx:cat:InvalidGeoValue";
  public static final String TITLE_INVALID_GEO_VALUE = "Geoquery value error";
  public static final String TITLE_INVALID_UUID = "Invalid syntax of uuid";



  public static final String DETAIL_INVALID_COORDINATE_POLYGON = "Coordinate mismatch (Polygon)";
  public static final String DETAIL_INVALID_BBOX = "Issue with bbox coordinates";
  public static final String DETAIL_INVALID_GEO_PARAMETER = "Missing/Invalid geo parameters";
  public static final String DETAIL_INVALID_RESPONSE_FILTER =
          "Missing/Invalid responseFilter parameters";

  public static final String DETAIL_INVALID_TOKEN = "Authorization failed, Invalid token.";

  public static final String TYPE_INVALID_PROPERTY_PARAM = "urn:dx:cat:InvalidProperty";
  public static final String TITLE_INVALID_PROPERTY_PARAM = "Invalid Property";
  public static final String TYPE_INVALID_PROPERTY_VALUE = "urn:dx:cat:InvalidPropertyValue";
  public static final String TITLE_INVALID_PROPERTY_VALUE = "Invalid Property Values";

  public static final String TYPE_INVALID_QUERY_PARAM_VALUE = "urn:dx:cat:InvalidParamValue";
  public static final String TYPE_INVALID_UUID = "urn:dx:cat:InvalidUUID";

  public static final String TITLE_INVALID_QUERY_PARAM_VALUE = "Invalid value for a query param";
  public static final String TYPE_BAD_TEXT_QUERY = "urn:dx:cat:BadTextQuery";
  public static final String TITLE_BAD_TEXT_QUERY = "Bad text query values";

  public static final String TYPE_BAD_FILTER = "urn:dx:cat:BadFilter";
  public static final String TITLE_BAD_FILTER = "Bad filters applied";
  public static final String INSTANCE_CREATION_SUCCESS = "Instance created successfully.";
  public static final String WRONG_INSTANCE_NAME = "Requested Body Instance Name wrong";
  public static final String TITLE_WRONG_INSTANCE_NAME = "Inavlid Requested Body";
  public static final String TITLE_DB_ERROR = "database error";
  public static final String TYPE_INVALID_SEARCH_ERROR = "urn:dx:cat:InvalidRelationSearch";
  public static final String TITLE_INVALID_SEARCH_ERROR = "Invalid relationship type search";
  public static final String STAC_CREATION_SUCCESS = "Stac created successfully.";
  public static final String STAC_DELETION_SUCCESS = "Stac deleted successfully.";
  public static final String DETAIL_INVALID_SCHEMA = "Invalid schema provided";
  public static final String NO_CONTENT_AVAILABLE = "No Content Available";


}

