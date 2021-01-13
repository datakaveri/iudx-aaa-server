package iudx.aaa.server.apiserver.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.aaa.server.apiserver.dto.RevokeAllRequestDTO;
import iudx.aaa.server.apiserver.dto.RevokeRequestDTO;
import iudx.aaa.server.apiserver.dto.TokenRequestDTO;
import iudx.aaa.server.token.TokenService;

public class TokenAPIService {

  private TokenService tokenService;

  public TokenAPIService(TokenService tokenService) {
    this.tokenService = tokenService;
  }
  
  private JsonObject notImplemented() {
    return new JsonObject().put("status","token service verticle not implemented");
  }

  /**
   * Request for a token to access protected resources
   * 
   * @param context
   */
  public Future<JsonObject> getToken(TokenRequestDTO tokenRequest) {
    Promise<JsonObject> promise = Promise.promise();
    JsonObject request=JsonObject.mapFrom(tokenRequest);
    promise.complete(notImplemented());
    
    /*
     * tokenService.createToken(JsonObject.mapFrom(tokenRequest), handler->{ if(handler.succeeded())
     * { promise.complete(); }else { promise.complete(); } });
     */
    return promise.future();
  }

  /**
   * introspect tokens to get the resource IDs, APIs etc.
   * 
   * @param context
   */
  public void interospect(RoutingContext context) {

  }

  /**
   * Revoke tokens using token obtained from audit API
   * 
   * @param context
   */
  public Future<JsonObject> revoke(RevokeRequestDTO revokeRequest) {
    Promise<JsonObject> promise = Promise.promise();
    JsonObject request=JsonObject.mapFrom(revokeRequest);
    promise.complete(notImplemented());
//    tokenService.revokeToken(request, handler->{
//     promise.complete();
//    });
    return promise.future();
  }

  /**
   * Revoke all tokens issued to a particular consumer based on their certificate.
   * 
   * @param context
   */
  public Future<JsonObject> revokeAll(RevokeAllRequestDTO revokeAll) {
    Promise<JsonObject> promise=Promise.promise();
    JsonObject request=JsonObject.mapFrom(revokeAll);
    promise.complete(notImplemented());
    return promise.future();
  }

  /**
   * get details of the tokens issued for the resources
   * 
   * @param context
   */
  public void audit(RoutingContext context) {

  }
}
