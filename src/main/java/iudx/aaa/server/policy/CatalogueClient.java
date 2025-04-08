package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.ERR_NOT_VALID_RESOURCE;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.UUID_REGEX;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.models.ResourceObj;
import iudx.aaa.server.apiserver.models.ResourceObj.ResourceObjBuilder;
import iudx.aaa.server.apiserver.models.Response;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CatalogueClient {
  private static final Logger LOGGER = LogManager.getLogger(CatalogueClient.class);

  public static final String CAT_ITEM_ENDPOINT = "/item";
  public static final String CAT_RELATION_ENDPOINT = "/relationship";

  public static final String CAT_REL_QUERY_PARAM = "rel";
  public static final String CAT_REL_QUERY_VAL_ALL = "all";

  public static final String CAT_RESP_TYPE_KEY = "type";
  public static final String CAT_RESP_APD_KEY = "apdURL";
  public static final String CAT_RESP_ACCESS_POLICY_KEY = "accessPolicy";
  public static final String CAT_RESP_RES_SERVER_URL_KEY = "resourceServerRegURL";
  public static final String CAT_RESP_RES_GROUP_KEY = "resourceGroup";
  public static final String CAT_RESP_PROVIDER_USER_ID_KEY = "ownerUserId";
  public static final String CAT_SUCCESS_URN = "urn:dx:cat:Success";

  public static final String CAT_RESP_RESOURCE_TYPE = "iudx:Resource";
  public static final String CAT_RESP_PROVIDER_TYPE = "iudx:Provider";
  public static final String CAT_RESP_RES_SERVER_TYPE = "iudx:ResourceServer";

  private final WebClient client;
  private final String catHost;
  private final Integer catPort;
  private final String catBasePath;

  public CatalogueClient(WebClient client, JsonObject options) {

    this.client = client;
    this.catHost = options.getString("catServerHost");
    this.catPort = Integer.parseInt(options.getString("catServerPort"));
    this.catBasePath = options.getString("catServerBasePath");
  }

  /**
   * Checks if given resource ID is a valid resource, gets all info about the resource and puts it
   * into a {@link ResourceObj} object.
   *
   * @param itemId a UUID representing a resource
   * @return a Future of {@link ResourceObj} object containing all info if successful
   */
  public Future<ResourceObj> getResourceDetails(UUID itemId) {
    Promise<ResourceObj> promise = Promise.promise();

    ResourceObjBuilder builder = new ResourceObjBuilder();

    Future<JsonArray> catExistenceResponse =
        client
            .get(catPort, catHost, catBasePath + CAT_ITEM_ENDPOINT)
            .addQueryParam(ID, itemId.toString())
            .send()
            .compose(
                res -> {
                  if (res.statusCode() == 200
                      && CAT_SUCCESS_URN.equals(res.bodyAsJsonObject().getString(TYPE))) {
                    return Future.succeededFuture(res.bodyAsJsonObject().getJsonArray(RESULTS));
                  } else if (res.statusCode() == 404) {
                    Response r =
                        new Response.ResponseBuilder()
                            .type(Urn.URN_INVALID_INPUT.toString())
                            .title(ITEMNOTFOUND)
                            .detail(itemId.toString())
                            .status(400)
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  } else {
                    LOGGER.error(
                        "Failed Catalogue item check : {} {}",
                        res.statusCode(),
                        res.bodyAsString());
                    return Future.failedFuture(INTERNALERROR);
                  }
                });

    Future<JsonObject> itemValidation =
        catExistenceResponse.compose(
            resArr -> {
              if (resArr.isEmpty()) {
                LOGGER.error("Failed Catalogue item check : Results array empty");
                return Future.failedFuture(INTERNALERROR);
              }

              JsonObject body = resArr.getJsonObject(0);

              JsonArray itemTypes = body.getJsonArray(CAT_RESP_TYPE_KEY);

              if (!itemTypes.contains(CAT_RESP_RESOURCE_TYPE)) {
                Response r =
                    new Response.ResponseBuilder()
                        .type(Urn.URN_INVALID_INPUT.toString())
                        .title(ERR_NOT_VALID_RESOURCE)
                        .detail(itemId.toString())
                        .status(400)
                        .build();
                return Future.failedFuture(new ComposeException(r));
              }

              if (!body.containsKey(CAT_RESP_APD_KEY)) {
                LOGGER.error(
                    "Failed Catalogue item check : Resource {} does not have `apd` key",
                    itemId.toString());
                return Future.failedFuture(INTERNALERROR);
              }

              if (!body.containsKey(CAT_RESP_ACCESS_POLICY_KEY)) {
                LOGGER.error(
                    "Failed Catalogue item check : Resource {} does not have `accessPolicy` key",
                    itemId.toString());
                return Future.failedFuture(INTERNALERROR);
              }

              if (!(body.containsKey(CAT_RESP_RES_GROUP_KEY)
                  && body.getString(CAT_RESP_RES_GROUP_KEY).matches(UUID_REGEX))) {
                LOGGER.error(
                    "Failed Catalogue item check : Resource {} does not have `resourceGroup` key or is not UUID",
                    itemId.toString());
                return Future.failedFuture(INTERNALERROR);
              }

              builder.id(itemId);
              builder.apdUrl(body.getString(CAT_RESP_APD_KEY));
              builder.resGrpId(UUID.fromString(body.getString(CAT_RESP_RES_GROUP_KEY)));
              builder.accessType(body.getString(CAT_RESP_ACCESS_POLICY_KEY));
              builder.itemType(ItemType.RESOURCE);

              return Future.succeededFuture();
            });

    Future<JsonArray> catRelationResponse =
        itemValidation.compose(
            itemExists ->
                client
                    .get(catPort, catHost, catBasePath + CAT_RELATION_ENDPOINT)
                    .addQueryParam(ID, itemId.toString())
                    .addQueryParam(CAT_REL_QUERY_PARAM, CAT_REL_QUERY_VAL_ALL)
                    .send()
                    .compose(
                        res -> {
                          if (res.statusCode() == 200
                              && CAT_SUCCESS_URN.equals(res.bodyAsJsonObject().getString(TYPE))) {
                            return Future.succeededFuture(
                                res.bodyAsJsonObject().getJsonArray(RESULTS));
                          } else {
                            LOGGER.error(
                                "Failed Catalogue relation check : {} {}",
                                res.statusCode(),
                                res.bodyAsJsonObject().toString());
                            return Future.failedFuture(INTERNALERROR);
                          }
                        }));

    Future<ResourceObj> relationValidation =
        catRelationResponse.compose(
            resArr -> {
              if (resArr.isEmpty()) {
                LOGGER.error("Failed Catalogue relation check : Results array empty");
                return Future.failedFuture(INTERNALERROR);
              }

              String providerUserId = "";
              String resourceServerUrl = "";

              for (int i = 0; i < resArr.size(); i++) {
                JsonObject json = resArr.getJsonObject(i);
                if (json.getJsonArray(CAT_RESP_TYPE_KEY).contains(CAT_RESP_RES_SERVER_TYPE)) {
                  resourceServerUrl = json.getString(CAT_RESP_RES_SERVER_URL_KEY, "");
                } else if (json.getJsonArray(CAT_RESP_TYPE_KEY).contains(CAT_RESP_PROVIDER_TYPE)) {
                  providerUserId = json.getString(CAT_RESP_PROVIDER_USER_ID_KEY, "");
                }
              }

              if (providerUserId.isEmpty() || resourceServerUrl.isEmpty()) {
                LOGGER.error(
                    "Failed Catalogue relation check : relationship API - provider {}, rsURL {}",
                    providerUserId,
                    resourceServerUrl);
                return Future.failedFuture(INTERNALERROR);
              }

              // getting res group ID from item API
              builder.ownerId(UUID.fromString(providerUserId));
              builder.resServerUrl(resourceServerUrl);

              return Future.succeededFuture(builder.build());
            });

    relationValidation
        .onSuccess(
            res -> {
              promise.complete(res);
            })
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
}
