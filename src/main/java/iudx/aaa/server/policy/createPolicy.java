package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.util.ComposeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.policy.Constants.CHECKUSEREXIST;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_APD_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_EXISTING_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_RES_SER;
import static iudx.aaa.server.policy.Constants.CHECK_TRUSTEE_POLICY;
import static iudx.aaa.server.policy.Constants.DUPLICATE_POLICY;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.INVALID_USER;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.NO_AUTH_TRUSTEE_POLICY;
import static iudx.aaa.server.policy.Constants.SERVER_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.VALIDATE_EXPIRY_FAIL;
import static iudx.aaa.server.policy.Constants.itemTypes;
import static iudx.aaa.server.policy.Constants.status;

public class createPolicy {
  private static final Logger LOGGER = LogManager.getLogger(createPolicy.class);

  private final PgPool pool;
  private final JsonObject options;

  public createPolicy(PgPool pool, JsonObject options) {
    this.pool = pool;
    this.options = options;
  }

  /**
   * checks if there is a policy for user by auth admin
   *
   * @param userId - userId of user
   * @return Boolean - active policy exits true , else false
   */
  public Future<Boolean> checkAuthPolicy(String userId) {
    Promise<Boolean> p = Promise.promise();
    pool.withConnection(
        conn ->
        conn.preparedQuery(CHECK_AUTH_POLICY)
        .execute(Tuple.of(userId, options.getString("authServerUrl"), status.ACTIVE))
        .onFailure(
            obj -> {
              LOGGER.error("checkAuthPolicy db fail :: " + obj.getLocalizedMessage());
              p.fail(INTERNALERROR);
            })
        .onSuccess(
            obj -> {
              if (obj.rowCount() > 0) p.complete(true);
              else {
                Response r =
                    new Response.ResponseBuilder()
                    .type(URN_INVALID_INPUT)
                    .title(NO_AUTH_POLICY)
                    .detail(NO_AUTH_POLICY)
                    .status(403)
                    .build();
                p.fail(new ComposeException(r));
              }
            }));
    return p.future();
  }

  /**
   * Insert into policy table or the apd_policies table
   *
   * @param query - query to insert
   * @param tup - list of tuples
   * @return Boolean - true if insertion is true
   */
  public Future<Boolean> insertPolicy(String query, List<Tuple> tup) {
    Promise<Boolean> p = Promise.promise();
    pool.withTransaction(conn -> conn.preparedQuery(query).executeBatch(tup).mapEmpty())
        .onFailure(
            failureHandler -> {
              LOGGER.error("insertPolicy fail :: " + failureHandler.getLocalizedMessage());
              p.fail(INTERNALERROR);
            })
        .onSuccess(success -> p.complete(true));

    return p.future();
  }

  /**
   * Validate the role of the user setting the policies based on the kind of policies they are
   * setting (admin policies, APD-trustee policies, res/res_group policies).
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> roleItemRelationValidations(CreatePolicyBar bar) {
    List<Roles> roles = bar.getPolicySetter().getRoles();
    List<CreatePolicyContext> context = bar.getContext();

    if (!roles.contains(Roles.ADMIN) && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE) && !roles.contains(Roles.TRUSTEE)) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).status(403).build();
      return Future.failedFuture(new ComposeException(r));
    }

    long adminPolicies = context.stream().map(i -> i.getRequest().getItemType())
        .filter(type -> type.equalsIgnoreCase(itemTypes.RESOURCE_SERVER.toString())).count();

    if (adminPolicies != 0 && adminPolicies != context.size()) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(INVALID_INPUT)
          .detail("All requests must be for resource server").status(400).build();
      return Future.failedFuture(new ComposeException(r));
    }

    if (adminPolicies == context.size() && !roles.contains(Roles.ADMIN)) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).status(403).build();
      return Future.failedFuture(new ComposeException(r));
    }

    long apdPolicies = context.stream().map(i -> i.getRequest().getItemType())
        .filter(type -> type.equalsIgnoreCase(itemTypes.APD.toString())).count();

    if (apdPolicies != 0 && apdPolicies != context.size()) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(INVALID_INPUT)
          .detail("All requests must be for APD").status(400).build();
      return Future.failedFuture(new ComposeException(r));
    }

    if (apdPolicies == context.size() && !roles.contains(Roles.TRUSTEE)) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).status(403).build();
      return Future.failedFuture(new ComposeException(r));
    }
    
    /* if setting res/res-group policies, must have provider/delegate role */
    if (adminPolicies == 0 && apdPolicies == 0 && !roles.contains(Roles.PROVIDER)
        && !roles.contains(Roles.DELEGATE)) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).status(403).build();
      return Future.failedFuture(new ComposeException(r));
    }

    return Future.succeededFuture(bar);
  }
  
  /**
   * Check for duplicates in the request itself.
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkDuplicateReqs(CreatePolicyBar bar) {

    List<CreatePolicyContext> context = bar.getContext();
    List<CreatePolicyRequest> duplicates = context.stream().map(val -> val.getRequest())
        .collect(Collectors.groupingBy(
            p -> p.getUserId() + "-" + p.getItemId() + "-" + p.getItemType(), Collectors.toList()))
        .values().stream().filter(i -> i.size() > 1).flatMap(j -> j.stream())
        .collect(Collectors.toList());

    if (duplicates.size() > 0) {
      Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(DUPLICATE)
          .detail(duplicates.get(0).toString()).status(400).build();
      return Future.failedFuture(new ComposeException(r));
    }

    return Future.succeededFuture(bar);
  }
 
  /**
   * Validate expiry if set in the request, else set a default expiry based on the kind of policy
   * being set.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> validateExpiry(CreatePolicyBar bar) {
    List<CreatePolicyContext> context = bar.getContext();

    for (int i = 0; i < context.size(); i++) {
      String expiry = context.get(i).getRequest().getExpiryTime();

      if (!expiry.isEmpty()) {
        LocalDateTime expTime = LocalDateTime.parse(expiry, DateTimeFormatter.ISO_DATE_TIME);

        if (expTime.compareTo(LocalDateTime.now(ZoneOffset.UTC)) < 0) {
          Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT)
              .title(VALIDATE_EXPIRY_FAIL).detail(expTime.toString()).status(400).build();

          return Future.failedFuture(new ComposeException(r));
        }

        context.get(i).setExpiryTime(expTime);
      } else {
        String itemType = context.get(i).getRequest().getItemType();
        LocalDateTime defaultExpiry;
        
        if (itemType.equals(itemTypes.RESOURCE_SERVER.toString())) {
          defaultExpiry = LocalDateTime.now(ZoneOffset.UTC)
              .plusMonths(Integer.parseInt(options.getString("adminPolicyExpiry")));
        } else {
          defaultExpiry = LocalDateTime.now(ZoneOffset.UTC)
              .plusMonths(Integer.parseInt(options.getString("policyExpiry")));
        }
        context.get(i).setExpiryTime(defaultExpiry);
      }

    }
    
    bar.setContext(context);
    return Future.succeededFuture(bar);
  }
 
  /**
   * Check that the users in the user policy requests exist.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkUsersExist(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();

    List<CreatePolicyContext> context = bar.getContext();

    Set<UUID> userIds = context.stream().map(r -> r.getUserId())
        .collect(Collectors.toSet());
    userIds.remove(UUID.fromString(NIL_UUID));

    if (userIds.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    Collector<Row, ?, Set<UUID>> userIdCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toSet());

    pool.withConnection(conn -> conn.preparedQuery(CHECKUSEREXIST).collecting(userIdCollector)
        .execute(Tuple.of(userIds.toArray(UUID[]::new))).map(res -> res.value()).onSuccess(obj -> {
          if (obj.size() != userIds.size()) {
            
            userIds.removeAll(obj);
            String offendingIds = userIds.toString();
            
            Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT).title(INVALID_USER)
                .detail(offendingIds).status(400).build();
            promise.fail(new ComposeException(r));
          } else {
            promise.complete(bar);
          }
        })).onFailure(obj -> {
          LOGGER.error("checkUserExist db fail :: " + obj.getLocalizedMessage());
          promise.fail(INTERNALERROR);
        });
    return promise.future();
  }
 
  /**
   * Check that the resource servers in admin policies exist and that the user setting the admin
   * policies owns them.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkResServersExistAndOwnership(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();

    List<CreatePolicyContext> context = bar.getContext();
    Set<String> serverUrls = context.stream().filter(
        i -> i.getRequest().getItemType().equalsIgnoreCase(itemTypes.RESOURCE_SERVER.toString()))
        .map(j -> j.getRequest().getItemId()).collect(Collectors.toSet());

    if (serverUrls.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    UUID ownerId = UUID.fromString(bar.getPolicySetter().getUserId());

    Collector<Row, ?, Map<String, ResourceObj>> resDetailCollector =
        Collectors.toMap(row -> row.getString("cat_id"), row -> {
          return new ResourceObj(
              row.toJson().put(ITEMTYPE, itemTypes.RESOURCE_SERVER.toString().toLowerCase()));
        });

    pool.withConnection(conn -> conn.preparedQuery(CHECK_RES_SER).collecting(resDetailCollector)
        .execute(Tuple.of(ownerId).addArrayOfString(serverUrls.toArray(String[]::new)))
        .map(i -> i.value()).onSuccess(res -> {
          if (res.size() != serverUrls.size()) {
            Set<String> urls = res.keySet();
            serverUrls.removeAll(urls);
            Response r = new Response.ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(SERVER_NOT_PRESENT).detail(serverUrls.toString()).build();
            promise.fail(new ComposeException(r));
          } else {
            for (int i = 0; i < context.size(); i++) {
              CreatePolicyContext val = context.get(i);
              if (val.getRequest().getItemType()
                  .equalsIgnoreCase(itemTypes.RESOURCE_SERVER.toString())) {
                context.get(i).setItem(res.get(val.getRequest().getItemId()));
              }
            }
          }

          bar.setContext(context);
          promise.complete(bar);
          
        })).onFailure(obj -> {
          LOGGER.error("checkResSer db fail :: " + obj.getLocalizedMessage());
          promise.fail(INTERNALERROR);
        });

    return promise.future();
  }
 
  /**
   * Check that an auth admin policy exists for the user setting policies.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkAuthPolicy(CreatePolicyBar bar)
  {
    Promise<CreatePolicyBar> promise = Promise.promise();
    /*
     * if setting policies where the item type is resource server i.e admin policies, skip this
     * check. If any of the policies have itemType resource_server, we complete the promise, since
     * all must be admin policies in this case.
     */
    Boolean adminPols = bar.getContext().stream().anyMatch(
        i -> i.getItem().getItemType().equalsIgnoreCase(itemTypes.RESOURCE_SERVER.toString()));
    if (adminPols) {
      promise.complete(bar);
      return promise.future();
    }
    
    checkAuthPolicy(bar.getPolicySetter().getUserId()).onSuccess(res -> promise.complete(bar))
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
 
  /**
   * For users setting APD policies, check that the trustee of the APD has set an
   * <b><tt>itemType='APD'</tt></b> policy for the user.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkAuthTrusteePolicy(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();
    List<CreatePolicyContext> context = bar.getContext();

    UUID providerId;
    if (bar.isDelegated()) {
      providerId = bar.getDelegatorId();
    } else {
      providerId = UUID.fromString(bar.getPolicySetter().getUserId());
    }

    Set<UUID> apdIds = context.stream().filter(i -> i.getUserId().equals(UUID.fromString(NIL_UUID)))
        .map(j -> j.getApd().getId()).collect(Collectors.toSet());

    if (apdIds.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    pool.withConnection(conn -> conn.preparedQuery(CHECK_TRUSTEE_POLICY)
        .execute(Tuple.of(providerId, status.ACTIVE, apdIds.toArray(UUID[]::new)))
        .onFailure(obj -> {
          LOGGER.error("checkAuthTrusteePolicy db fail :: " + obj.getLocalizedMessage());
          promise.fail(INTERNALERROR);
        }).onSuccess(obj -> {
          if (obj.rowCount() == apdIds.size())
            promise.complete(bar);
          else {
            Response r = new Response.ResponseBuilder().type(URN_INVALID_INPUT)
                .title(NO_AUTH_TRUSTEE_POLICY).detail(NO_AUTH_TRUSTEE_POLICY).status(403).build();
            promise.fail(new ComposeException(r));
          }
        }));
    return promise.future();
  }
 
  /**
   * Check if the user policies to be set do not already exist. Checks if policies exist and are
   * active for <b> user + item ID + item type + owner </b>.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkExistingUserPolicies(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();
    List<CreatePolicyContext> context = bar.getContext();

    List<Tuple> tupList =
        context.stream().filter(x -> !x.getUserId().equals(UUID.fromString(NIL_UUID))).map(i -> {

          UUID userId = i.getUserId();
          ResourceObj item = i.getItem();
          UUID itemId = item.getId();
          UUID ownerId = item.getOwnerId();
          String itemType = item.getItemType().toUpperCase();
          return Tuple.of(userId, itemId, itemType, ownerId, "ACTIVE");
        }).collect(Collectors.toList());

    if (tupList.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    pool.withTransaction(conn -> conn.preparedQuery(CHECK_EXISTING_POLICY).executeBatch(tupList)
        .onFailure(failureHandler -> {
          LOGGER.error("checkExistingPolicy fail :: " + failureHandler.getLocalizedMessage());
          promise.fail(INTERNALERROR);
        }).onSuccess(ar -> {
          RowSet<Row> rows = ar;
          List<UUID> ids = new ArrayList<>();
          while (rows != null) {
            rows.iterator().forEachRemaining(row -> {
              ids.add(row.getUUID(ID));
            });
            rows = rows.next();
          }

          if (ids.size() > 0) {
            Response r = new Response.ResponseBuilder().type(URN_ALREADY_EXISTS)
                .title(DUPLICATE_POLICY).detail(ids.get(0).toString()).status(409).build();
            promise.fail(new ComposeException(r));
          } else {
            promise.complete(bar);
          }
        }));
    return promise.future();
  }
 
  /**
   * Check if the APD policies to be set do not already exist. Checks if policies exist and are
   * active for <b>item ID + item type</b> (we only allow 1 APD policy to exist for a given item).
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> checkExistingApdPolicies(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();
    List<CreatePolicyContext> context = bar.getContext();

    List<Tuple> tupList =
        context.stream().filter(x -> x.getUserId().equals(UUID.fromString(NIL_UUID))).map(i -> {

          ResourceObj item = i.getItem();
          UUID itemId = item.getId();
          UUID ownerId = item.getOwnerId();
          String itemType = item.getItemType().toUpperCase();
          return Tuple.of(itemId, itemType, ownerId, "ACTIVE");
        }).collect(Collectors.toList());

    if (tupList.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    pool.withTransaction(conn -> conn.preparedQuery(CHECK_EXISTING_APD_POLICY).executeBatch(tupList)
        .onFailure(failureHandler -> {
          LOGGER.error("checkExistingApdPolicy fail :: " + failureHandler.getLocalizedMessage());
          promise.fail(INTERNALERROR);
        }).onSuccess(ar -> {
          RowSet<Row> rows = ar;
          List<UUID> ids = new ArrayList<>();
          while (rows != null) {
            rows.iterator().forEachRemaining(row -> {
              ids.add(row.getUUID(ID));
            });
            rows = rows.next();
          }

          if (ids.size() > 0) {
            Response r = new Response.ResponseBuilder().type(URN_ALREADY_EXISTS)
                .title(DUPLICATE_POLICY).detail(ids.get(0).toString()).status(409).build();
            promise.fail(new ComposeException(r));
          } else
            promise.complete(bar);
        }));
    return promise.future();
  }

  /**
   * Insert all user policies.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> insertUserPolicies(CreatePolicyBar bar) {
    Promise<CreatePolicyBar> promise = Promise.promise();
    List<CreatePolicyContext> context = bar.getContext();

    List<Tuple> tupList =
        context.stream().filter(x -> !x.getUserId().equals(UUID.fromString(NIL_UUID))).map(i -> {

          UUID userId = i.getUserId();
          ResourceObj item = i.getItem();
          UUID itemId = item.getId();
          UUID ownerId = item.getOwnerId();
          String itemType = item.getItemType().toUpperCase();
          LocalDateTime expiry = i.getExpiryTime();
          JsonObject constraints = i.getConstraints();

          return Tuple.of(userId, itemId, itemType, ownerId, "ACTIVE", expiry, constraints);
        }).collect(Collectors.toList());

    if (tupList.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }
    
    insertPolicy(INSERT_POLICY, tupList).onSuccess(x -> promise.complete(bar))
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
  
  /**
   * Insert all APD policies.
   * 
   * @param bar
   * @return
   */
  Future<CreatePolicyBar> insertApdPolicies(CreatePolicyBar bar)
  {
    Promise<CreatePolicyBar> promise = Promise.promise();
    List<CreatePolicyContext> context = bar.getContext();
    
    List<Tuple> tupList =
        context.stream().filter(x -> x.getUserId().equals(UUID.fromString(NIL_UUID))).map(i -> {

          UUID apdId = i.getApd().getId();
          String userClass = i.getUserClass();
          ResourceObj item = i.getItem();
          UUID itemId = item.getId();
          UUID ownerId = item.getOwnerId();
          String itemType = item.getItemType().toUpperCase();
          LocalDateTime expiry = i.getExpiryTime();
          JsonObject constraints = i.getConstraints();
          
          return Tuple.of(apdId, userClass, itemId, itemType, ownerId, "ACTIVE", expiry, constraints);
        }).collect(Collectors.toList());

    if (tupList.isEmpty()) {
      promise.complete(bar);
      return promise.future();
    }

    insertPolicy(INSERT_APD_POLICY, tupList).onSuccess(x -> promise.complete(bar))
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
  
}
