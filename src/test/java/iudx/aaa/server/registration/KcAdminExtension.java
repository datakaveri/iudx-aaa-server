package iudx.aaa.server.registration;

import java.io.IOException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class KcAdminExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

  public KcAdminInt kc;
  
  @Override
  public void beforeAll(ExtensionContext context) {
      try {
        kc = KcAdminInt.init();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
  }

  @Override
  public void afterAll(ExtensionContext context) {
      // Then shut everything down
  }
  
  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
      return parameterContext.getParameter().getType() == KcAdminInt.class;
  }

  @Override
  public KcAdminInt resolveParameter(ParameterContext parameterContext,
                                 ExtensionContext extensionContext) {
      return kc;
  }
}
