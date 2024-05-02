package iudx.aaa.server.registration;

import static io.restassured.RestAssured.basePath;
import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.enableLoggingOfRequestAndResponseIfValidationFails;
import static io.restassured.RestAssured.port;
import static io.restassured.RestAssured.proxy;

import io.restassured.RestAssured;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit5 extension to allow {@link RestAssured} configuration to be injected into all integration
 * tests using {@link ExtendWith}. Java properties can be passed in arguments when running the
 * integration tests to configure a host (<code>intTestHost</code>), port (<code>intTestPort</code>
 * ), proxy host (<code>intTestProxyHost</code>) and proxy port (<code>intTestProxyPort</code>).
 */
public class RestAssuredConfigExtension implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) {
    String testHost = System.getProperty("intTestHost");

    if (testHost != null) {
      baseURI = testHost;
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
