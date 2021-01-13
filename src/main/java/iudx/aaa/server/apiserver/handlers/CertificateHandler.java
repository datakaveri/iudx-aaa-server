package iudx.aaa.server.apiserver.handlers;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLPeerUnverifiedException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class CertificateHandler implements Handler<RoutingContext> {
  
  public static CertificateHandler create() {
    return new CertificateHandler();
  }

  @Override
  public void handle(RoutingContext context) {
    context.data().put("cert_info", getCertificateInfo(context));
    context.next();
  }

  private Map<String, String> getCertificateInfo(RoutingContext context) {
    Map<String,String> certAttributes=new HashMap<>();
    Principal peerPrincipal;
    try {
      peerPrincipal = context.request().connection().sslSession().getPeerPrincipal();
      String certificate_class[] = peerPrincipal.toString().split(",");
      String class_level = certificate_class[0];
      String emailInfo = certificate_class[1];
      String[] email = emailInfo.split("=")[1].split("@");
      String emailID = email[0];
      String domain = email[1];
      String cn = certificate_class[2];
      String cnValue = cn.split("=")[1];
      
      certAttributes.put("class", certificate_class.toString());
      certAttributes.put("class_level",class_level);
      certAttributes.put("emailInfo",emailInfo);
      certAttributes.put("email", email.toString());
      certAttributes.put("emailId", emailID);
      certAttributes.put("domain", domain);
      certAttributes.put("cn", cn);
      certAttributes.put("cnValue", cnValue);
      
    } catch (SSLPeerUnverifiedException e) {
      e.printStackTrace();
    }
   return certAttributes;
  }
}
