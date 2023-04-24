package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.EMAIL_BODY;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Email Client.
 *
 * <h1>Email Client</h1>
 *
 * <p>The Email Client assists the AAA server in managing email-related requests.
 *
 * @version 1.0
 * @since 2023-04-17
 */
public class EmailClient {

  private static final Logger LOGGER = LogManager.getLogger(EmailClient.class);
  private final String emailHostname;
  private final int emailPort;
  private final String emailUserName;
  private final String emailPassword;
  private final String senderEmail;
  private final String senderName;
  private final String supportEmail;
  private final String publisherPanelURL;
  private final MailClient mailClient;
  private final boolean notifyByEmail;
  /**
   * Constructs a new instance of the class.
   *
   * @param vertx
   * @param config
   */
  public EmailClient(Vertx vertx, JsonObject config) {
    this.emailHostname = config.getString("emailHostName");
    this.emailPort = config.getInteger("emailPort");
    this.emailUserName = config.getString("emailUserName");
    this.emailPassword = config.getString("emailPassword");
    this.senderEmail = config.getString("emailSender");
    this.supportEmail = config.getString("emailSupport");
    this.publisherPanelURL = config.getString("publisherPanelUrl");
    this.notifyByEmail = config.getBoolean("notifyByEmail");
    this.senderName = config.getString("senderName");

    MailConfig mailConfig = new MailConfig();
    mailConfig.setStarttls(StartTLSOptions.REQUIRED);
    mailConfig.setLogin(LoginOption.REQUIRED);
    mailConfig.setKeepAliveTimeout(5);
    mailConfig.setHostname(emailHostname);
    mailConfig.setPort(emailPort);
    mailConfig.setUsername(emailUserName);
    mailConfig.setPassword(emailPassword);
    mailConfig.setAllowRcptErrors(true);

    this.mailClient = MailClient.create(vertx, mailConfig);
  }

  /**
   * This method is utilized to initiate the email sending process to the provider and delegate
   * after the notification has been created.
   *
   * @param emailInfo
   * @return
   */
  public Future<Void> sendEmail(EmailInfo emailInfo) {
    Promise<Void> promise = Promise.promise();

    if (!notifyByEmail) {
      return promise.future();
    }
    UUID consumerId = emailInfo.getConsumerId();
    JsonObject consumer = emailInfo.getUserInfo(consumerId.toString());
    String consumerName =
        consumer.getJsonObject("name").getString("firstName")
            + " "
            + consumer.getJsonObject("name").getString("lastName");
    String consumerEmailId = consumer.getString("email");
    //Below is the Map<CatId,ExpiryTime> of a given request.
    Map<String, String> expiryDurationMap = emailInfo.getExpiryDurationMap();
    emailInfo
        .getItemDetails()
        .values()
        .forEach(
            resourceObj -> {
              UUID providerId = resourceObj.getOwnerId();
              JsonObject provider = emailInfo.getUserInfo(providerId.toString());
              String catId = resourceObj.getCatId();
              List<UUID> authDelegates =
                  emailInfo.getProviderIdToAuthDelegateId().get(providerId.toString());
              List<String> ccEmailIds = new ArrayList<>();
              ccEmailIds.add(supportEmail);
              // adding delegate email ids in ccEmailIds array list
              authDelegates.forEach(
                  authDelegatesuuid -> {
                    JsonObject delegate = emailInfo.getUserInfo(authDelegatesuuid.toString());
                    ccEmailIds.add(delegate.getString("email"));
                  });
              String emailBody =
                  EMAIL_BODY
                      .replace("${CONSUMER_NAME}", consumerName)
                      .replace("${CONSUMER_EMAIL}", consumerEmailId)
                      .replace("${REQUESTED_CAT_ID}", catId)
                      .replace("${PUBLISHER_PANEL_URL}", publisherPanelURL)
                      .replace("${TIME_DURATION}", expiryDurationMap.get(catId))
                      .replace("${SENDER'S_NAME}", senderName);

              final String providerEmailId = provider.getString("email");
              // creating mail object
              MailMessage providerMail = new MailMessage();
              providerMail.setFrom(senderEmail);
              providerMail.setTo(providerEmailId);
              providerMail.setCc(ccEmailIds);
              providerMail.setText(emailBody);
              providerMail.setSubject("Request for policy for " + catId);

              mailClient.sendMail(
                  providerMail,
                  providerMailSuccessHandler -> {
                    if (providerMailSuccessHandler.succeeded()) {
                      LOGGER.debug(
                          "email sent successfully : {} ", providerMailSuccessHandler.result());
                    } else {
                      promise.fail(providerMailSuccessHandler.cause().getLocalizedMessage());
                      LOGGER.error(
                          "Failed to send email because : {} ",
                          providerMailSuccessHandler.cause().getLocalizedMessage());
                    }
                  });
            });
    return promise.future();
  }
}
