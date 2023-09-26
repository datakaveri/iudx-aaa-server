package iudx.aaa.server.registration;

import java.io.IOException;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JUnit5 extension to allow Keycloak admin instance to be injected into all integration tests using
 * {@link ExtendWith}. This extension uses the ExtensionContext {@link Store} to store an instance
 * of a {@link KcAdminInt} object globally (in the JUnit root {@link ExtensionContext}).
 * This allows the {@link KcAdminInt} object to be instantiated only once, before any integration
 * test starts.
 * 
 * </br>
 * </br>
 * Additionally {@link KcAdminInt} implements {@link CloseableResource} so that at the end of all
 * tests, Keycloak users can be cleaned up automatically.
 *
 */
public class KcAdminExtension implements BeforeAllCallback, ParameterResolver {

  private static final Logger LOGGER = LogManager.getLogger(KcAdminExtension.class);
  private static final Namespace NAMESPACE = Namespace.create(KcAdminExtension.class);
  private static final String KC_EXT = "KC_EXTENSION_STORE_KEY";

  @Override
  public void beforeAll(ExtensionContext context) {
    if (context.getRoot().getStore(NAMESPACE).get(KC_EXT, KcAdminInt.class) == null) {
      try {
        KcAdminInt kc = KcAdminInt.init();
        context.getRoot().getStore(NAMESPACE).put(KC_EXT, kc);
        LOGGER.info("Created KcAdminInt object and placed in the JUnit root ExtensionContext store");
      } catch (IOException e) {
        LOGGER.error("Error when creating KcAdminInt object {}", e.getMessage());
      } catch (Exception e) {
        LOGGER.error("Error when creating KcAdminInt object {}", e.getMessage());
      }
    }
  }
  
  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) {
    return parameterContext.getParameter().getType() == KcAdminInt.class;
  }

  @Override
  public KcAdminInt resolveParameter(ParameterContext parameterContext,
      ExtensionContext context) {
    return context.getRoot().getStore(NAMESPACE).get(KC_EXT, KcAdminInt.class);
  }
}
