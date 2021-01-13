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

  // API Documentation endpoint
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String ROUTE_DOC = "/apis";

  // Accept Headers and CORS
  public static final String MIME_APPLICATION_JSON = "application/json";
  public static final String MIME_TEXT_HTML = "text/html";
  
  //rest endpoints
  //cert 
  public static final String API_CERT_DETAILS="/auth/v1/certificate-info";
  //token
  public static final String API_TOKEN="/auth/v1/token";
  public static final String API_INTROSPECT_TOKEN="/auth/v1/interospect";
  public static final String API_REVOKE_TOKEN="/auth/v1/token/revoke";
  public static final String API_REVOKE_ALL_TOKEN="/auth/v1/token/revoke-all";
  public static final String API_AUDIT_TOKEN="/auth/v1/audit/tokens";
  
  //Registration
  public static final String API_PROVIDER_REGISTRATION="/v1/provider/registration";
  public static final String API_COI_REGISTRATION="/v1/registration";
  public static final String API_ALL_ORG="/v1/organizations";
  
  //policy
  public static final String API_POLICY="/auth/v1/provider/access";
  
  //admin API
  public static final String API_ADMIN_ORG_REGISTER="/auth/v1/admin/organization";
  public static final String API_ADMIN_PROVIDERS_REGISTRATIONS="/auth/v1/admin/provider/registrations";
  public static final String API_ADMIN_PROVIDERS_UPDATE="/auth/v1/admin/provider/registrations/status";
  
  

}
