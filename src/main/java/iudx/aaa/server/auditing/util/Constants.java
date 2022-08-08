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
  /* Auditing Service Constants*/

  public static final String METHOD = "method";
  public static final String USER_ID = "userId";
  public static final String BODY = "body";
  public static final String API = "api";
  public static final String WRITE_QUERY =
      "INSERT INTO $0 (id,body,endpoint,method,time,userid) VALUES ('$1','$2','$3','$4',$5,'$6')";

  public static final String MESSAGE = "message";
  public static final String DATABASE_TABLE_NAME= "databaseTableName";
  public static final String READ_QUERY =
      "SELECT body,endpoint,method,time,userid from $0 where userid='$1'";
  public static final String START_TIME_QUERY = " and time>=$2";
  public static final String END_TIME_QUERY = " and time<=$3";
  public static final String ENDPOINT_QUERY = " and endpoint='$4'";
  public static final String METHOD_QUERY = " and method='$5'";

  /* Column indices depend on order of columns in READ_QUERY */
  public static final int BODY_COLUMN_INDEX = 0;
  public static final int ENDPOINT_COLUMN_INDEX = 1;
  public static final int METHOD_COLUMN_INDEX = 2;
  public static final int TIME_COLUMN_INDEX = 3;
  public static final int USERID_COLUMN_INDEX = 4;
}
