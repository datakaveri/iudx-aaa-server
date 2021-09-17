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
  public static final String RESULTS = "results";
  public static final String EMPTY_RESPONSE = "Empty response";

  /* Database */
  public static final String ERROR = "Error";
  public static final String QUERY_KEY = "query";
  public static final String DATA_NOT_FOUND = "Required Data not Found";
  public static final String USERID_NOT_FOUND = "UserID not found";
  public static final String START_TIME = "startTime";
  public static final String END_TIME = "endTime";
  public static final String ENDPOINT = "endPoint";
  public static final String TIME = "time";
  public static final String INVALID_DATE_TIME = "Date-Time not in correct format.";
  public static final String MISSING_START_TIME = "Start-Time not found.";
  public static final String MISSING_END_TIME = "End-Time not found.";
  public static final String INVALID_TIME = "End-Time cannot be before Start-Time.";
  public static final String METHOD_COLUMN_NAME = "(defaultdb.table_auditing.method)";
  public static final String TIME_COLUMN_NAME = "(defaultdb.table_auditing.time)";
  public static final String USERID_COLUMN_NAME = "(defaultdb.table_auditing.userid)";
  public static final String BODY_COLUMN_NAME = "(defaultdb.table_auditing.body)";
  public static final String ENDPOINT_COLUMN_NAME = "(defaultdb.table_auditing.endpoint)";

  /* Auditing Service Constants*/

  public static final String METHOD = "method";
  public static final String USER_ID = "userId";
  public static final String BODY = "body";
  public static final String API = "api";
  public static final String WRITE_QUERY =
      "INSERT INTO table_auditing (id,body,endpoint,method,time,userid) VALUES ('$1','$2','$3','$4',$5,'$6')";

  public static final String MESSAGE = "message";
  public static final String READ_QUERY =
      "SELECT body,endpoint,method,time,userid from table_auditing where userid='$1'";
  public static final String START_TIME_QUERY = " and time>=$2";
  public static final String END_TIME_QUERY = " and time<=$3";
  public static final String ENDPOINT_QUERY = " and endpoint='$4'";
  public static final String METHOD_QUERY = " and method='$5'";
}
