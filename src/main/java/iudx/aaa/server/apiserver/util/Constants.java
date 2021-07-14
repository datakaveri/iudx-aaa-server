package iudx.aaa.server.apiserver.util;

public class Constants {
  // Header params
  public static final String HEADER_TOKEN = "token";
  public static final String HEADER_HOST = "Host";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_ORIGIN = "Origin";
  public static final String HEADER_REFERER = "Referer";
  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String HEADER_OPTIONS = "options";
  
  /* Configuration & related */
  public static final String DATABASE_IP = "databaseIP";
  public static final String DATABASE_PORT = "databasePort";
  public static final String DATABASE_NAME = "databaseName";
  public static final String DATABASE_USERNAME = "databaseUserName";
  public static final String DATABASE_PASSWORD = "databasePassword";
  public static final String POOLSIZE = "poolSize";
  public static final String KEYSTORE_PATH = "keystorePath";
  public static final String KEYSTPRE_PASSWORD = "keystorePassword";
  public static final String AUTHSERVER_DOMAIN = "authServerDomain";
  public static final String KEYCLOACK_OPTIONS = "keycloakOptions";
  public static final int PG_CONNECTION_TIMEOUT = 10000;

  // API Documentation endpoint
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String ROUTE_DOC = "/apis";

  // Accept Headers and CORS
  public static final String MIME_APPLICATION_JSON = "application/json";
  public static final String MIME_TEXT_HTML = "text/html";
  
  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  
  /* API Server Routes */
  public static final String API_TOKEN="/auth/v1/token";
  public static final String API_INTROSPECT_TOKEN="/auth/v1/interospect";
  public static final String API_REVOKE_TOKEN="/auth/v1/token/revoke";
  
  
  /* SQL Queries */
  public static final String DB_SCHEMA = "test";
  public static final String SQL_GET_USER_ROLES = "SELECT u.id,  array_agg(r.role) as roles \n" + 
      "FROM (select id from " + DB_SCHEMA + ".users where keycloak_id = $1) u \n" + 
      "LEFT JOIN " + DB_SCHEMA+ ".roles r ON u.id = r.user_id\n" + 
      "where r.status='APPROVED' GROUP BY u.id";

}
