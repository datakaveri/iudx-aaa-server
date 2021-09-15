package iudx.aaa.server.auditing.util;

public class Constants {

  public static final String ID = "id";
  /* Errors */
  public static final String SUCCESS = "Success";
  public static final String FAILED = "Failed";
  public static final String DETAIL = "detail";
  public static final String ERROR_TYPE = "type";
  public static final String TITLE = "title";
  public static final String STATUS = "status";

  /* Database */
  public static final String ERROR = "Error";
  public static final String QUERY_KEY = "query";
  public static final String DATA_NOT_FOUND="Required Data not Found";
  /* Auditing Service Constants*/
  public static final String METHOD = "method";
  public static final String USER_ID = "userId";
  public static final String BODY = "body";
  public static final String API = "api";
  public static final String WRITE_QUERY =
      "INSERT INTO testing_table (id,body,endpoint,method,time,userid) VALUES ('$1','$2','$3','$4',$5,'$6')";
  public static final String MESSAGE = "message";
}
