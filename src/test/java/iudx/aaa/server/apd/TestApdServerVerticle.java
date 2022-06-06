package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.APD_READ_USERCLASSES_API;
import static iudx.aaa.server.apd.Constants.APD_REQ_USERCLASS;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_LINK;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.APD_VERIFY_API;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestApdServerVerticle extends AbstractVerticle {

  public static final int USERCLASS_ERRORS = 10;
  public static final int VERIFY_ERRORS = 22;
  private Router router = Router.router(vertx);
  private int userclassErrorCounter = 0;
  private int verifyErrorCounter = 0;
  private HttpServer server;

  private static Logger LOGGER = LogManager.getLogger(TestApdServerVerticle.class);

  @Override
  public void start() throws Exception {

    router.get(APD_READ_USERCLASSES_API).handler(context -> {
      userclassErrorCounter++;
      getUserClass(context, userclassErrorCounter);
    });

    router.post(APD_VERIFY_API).handler(BodyHandler.create()).handler(context -> {
      JsonObject body = context.getBodyAsJson();
      if (body.getString(APD_REQ_USERCLASS).equals("TestError")) {

        verifyErrorCounter++;
        postVerify(context, verifyErrorCounter);
      } else if (body.getString(APD_REQ_USERCLASS).equals("TestDeny")) {

        HttpServerResponse response = context.response();
        JsonObject jsonResponse =
            new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY).put(APD_RESP_DETAIL, "Error");
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
      } else if (body.getString(APD_REQ_USERCLASS).equals("TestDenyNInteraction")) {

        HttpServerResponse response = context.response();
        JsonObject jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT)
            .put(APD_RESP_DETAIL, "Error").put(APD_RESP_SESSIONID, UUID.randomUUID().toString())
            .put(APD_RESP_LINK, "example.com");
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
      }
      //add else if allow with userClass
      else if(body.getString(APD_REQ_USERCLASS).equals("TestSuccessWConstraints")){
        HttpServerResponse response = context.response();
        JsonObject jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW).put(APD_CONSTRAINTS,new JsonObject());
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
      }
      else {
        HttpServerResponse response = context.response();
        JsonObject jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW);
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
      }
    });

    server = vertx.createHttpServer();

    server.requestHandler(router).listen(7331).onSuccess(success -> {
      LOGGER.debug("Info: Started APD test HTTP server");
    }).onFailure(err -> {
      LOGGER.fatal("Info: Failed to start HTTP server - " + err.getMessage());
    });
  }

  @Override
  public void stop() {
    server.close().onSuccess(success -> {
      LOGGER.debug("Info: Stopped APD test HTTP server");
    }).onFailure(err -> {
      LOGGER.fatal("Info: Failed to stop HTTP server - " + err.getMessage());
    });
  }

  private void postVerify(RoutingContext context, int counter) {
    HttpServerResponse response = context.response();
    JsonObject jsonResponse;
    //add cases
    switch (counter) {
      case 1:
        response.setStatusCode(200).putHeader("Content-type", "application/html")
            .end("<html></html>");
        break;
      case 2:
        vertx.setTimer(7000, res -> {
          response.setStatusCode(200).putHeader("Content-type", "application/json").end("{}");
        });
        break;
      case 3:
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end("this is not JSON");
        break;
      case 4:
        response.setStatusCode(301).putHeader("Location", "example.com").end();
        break;
      case 5:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end("[]");
        break;
      case 6:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end();
        break;
      case 7:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end("{}");
        break;
      case 8:
        response.setStatusCode(100).putHeader("Content-type", "application/json").end("{}");
        break;
      case 9:
        response.setStatusCode(200).putHeader("Content-type",
            "multipart/form-data; boundary=---------------------------974767299852498929531610575")
            .end("{}");
        break;
      case 10:
        response.setStatusCode(200).putHeader("Content-type", "application/x-www-form-urlencoded")
            .end("{}");
        break;
      case 11:
        /* invalid URN */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, "urn:apd:random");
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 12:
        /* invalid URN */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, "urn:apd:random");
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 13:
        /* non 200/403 status code */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW);
        response.setStatusCode(401).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 14:
        /* sending 200, but wrong URN */
        jsonResponse =
            new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY).put(APD_RESP_DETAIL, "something");
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 15:
        /* sending 403, but wrong URN */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW);
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 16:
        /* sending 403+deny, but no detail */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY);
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 17:
        /* sending 403+denyNeedsInteraction, but no detail */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT);
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 18:
        /* sending 403+denyNeedsInteraction, but no sessionId */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT)
            .put(APD_RESP_DETAIL, "something");
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 19:
        /* sending 403+denyNeedsInteraction and sessionId, but no detail */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT)
            .put(APD_RESP_SESSIONID, "something");
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 20:
        /* sending denyNeedsInteraction, sessionId, detail but 200 */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT)
            .put(APD_RESP_SESSIONID, "something").put(APD_RESP_DETAIL, "something");
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case 21:
        /* sending allow with constraints, apdConstraints is not a JsonObject */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW)
            .put(APD_RESP_SESSIONID, "something").put(APD_RESP_DETAIL, "something").put(APD_CONSTRAINTS, new JsonArray());
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
      case VERIFY_ERRORS:
        /* sending nulls in response */
        jsonResponse = new JsonObject().put(APD_RESP_TYPE, null).put(APD_RESP_DETAIL, "something");
        response.setStatusCode(403).putHeader("Content-type", "application/json")
            .end(jsonResponse.encode());
        break;
    }
    return;
  }

  private void getUserClass(RoutingContext context, int counter) {
    HttpServerResponse response = context.response();
    switch (counter) {
      case 1:
        response.setStatusCode(200).putHeader("Content-type", "application/html")
            .end("<html></html>");
        break;
      case 2:
        vertx.setTimer(7000, res -> {
          response.setStatusCode(200).putHeader("Content-type", "application/json").end("{}");
        });
        break;
      case 3:
        response.setStatusCode(200).putHeader("Content-type", "application/json")
            .end("this is not JSON");
        break;
      case 4:
        response.setStatusCode(301).putHeader("Location", "example.com").end();
        break;
      case 5:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end("[]");
        break;
      case 6:
        response.setStatusCode(200).putHeader("Content-type", "text/html").end("{}");
        break;
      case 7:
        response.setStatusCode(200).putHeader("Content-type",
            "multipart/form-data; boundary=---------------------------974767299852498929531610575")
            .end("{}");
        break;
      case 8:
        response.setStatusCode(200).putHeader("Content-type", "application/x-www-form-urlencoded")
            .end("{}");
        break;
      case 9:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end();
        break;
      case USERCLASS_ERRORS:
        response.setStatusCode(400).putHeader("Content-type", "application/json").end("{}");
        break;
      default:
        response.setStatusCode(200).putHeader("Content-type", "application/json").end("{}");
    }
    return;
  }

}
