package iudx.aaa.server.certificate;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.tip.TIPService;

/**
 * The Certificate Service.
 * <h1>Certificate Service</h1>
 * <p>
 * The Certificate Service in the IUDX AAA Server defines the operations to be performed for
 * certificate generation, decoding etc.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.codegen.annotations.ProxyGen
 * @see io.vertx.codegen.annotations.VertxGen
 * @since 2020-12-15
 */

@VertxGen
@ProxyGen
public interface CertificateService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return CertificateServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static CertificateService createProxy(Vertx vertx, String address) {
    return new CertificateServiceVertxEBProxy(vertx, address);
  }

  /**
   * The validateCertificate implements the certificate validation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return CertificateService which is a Service
   */

  @Fluent
  CertificateService validateCertificate(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The generateCertificate implements the certificate generation operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return CertificateService which is a Service
   */

  @Fluent
  CertificateService generateCertificate(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler);

  /**
   * The createCSR implements the certificate signing operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return CertificateService which is a Service
   */

  @Fluent
  CertificateService createCSR(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  /**
   * The createCSR implements the certificate signing update operation.
   * 
   * @param request which is a JsonObject
   * @param handler which is a Request Handler
   * @return CertificateService which is a Service
   */

  @Fluent
  CertificateService updateCSR(JsonObject request, Handler<AsyncResult<JsonObject>> handler);


}
