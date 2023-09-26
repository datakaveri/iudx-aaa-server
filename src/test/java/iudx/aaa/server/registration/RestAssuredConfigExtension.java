package iudx.aaa.server.registration;

import static io.restassured.RestAssured.basePath;
import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.enableLoggingOfRequestAndResponseIfValidationFails;
import static io.restassured.RestAssured.port;
import static io.restassured.RestAssured.proxy;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class RestAssuredConfigExtension implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) {
    String testHost = System.getProperty("intTestHost");

    if (testHost != null) {
      baseURI = "http://" + testHost;
    } else {
      baseURI = "http://localhost";
    }

    String testPort = System.getProperty("intTestPort");

    if (testPort != null) {
      port = Integer.parseInt(testPort);
    } else {

      port = 8443;
    }

    basePath = "/auth/v1";

    String proxyHost = System.getProperty("intTestProxyHost");
    String proxyPort = System.getProperty("intTestProxyPort");

    if (proxyHost != null && proxyPort != null) {
      proxy(proxyHost, Integer.parseInt(proxyPort));
    }

    enableLoggingOfRequestAndResponseIfValidationFails();
  }
}
