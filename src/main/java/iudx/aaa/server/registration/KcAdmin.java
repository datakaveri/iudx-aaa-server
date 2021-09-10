package iudx.aaa.server.registration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.apiserver.Roles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Keycloak Admin Client to allow a client that has admin capabilities to connect to Keycloak and
 * read/modify entities on Keycloak.
 *
 */
public class KcAdmin {

  Keycloak keycloak;
  String realm = "";

  /**
   * Create an instance of the Keycloak Admin client. Verticles can call the constructor with params
   * from it's config.
   * 
   * @param serverUrl the Keycloak URL. Must be of the format (http/https)://(domain)/auth
   * @param realm the name of the keycloak realm. The realm must have the required roles configured
   * @param clientId the client ID of the admin client
   * @param clientSecret the client secret of the admin client
   * @param poolSize the pool size for the resteasy connection pool
   */

  public KcAdmin(String serverUrl, String realm, String clientId, String clientSecret,
      int poolSize) {

    this.realm = realm;
    keycloak = KeycloakBuilder.builder().serverUrl(serverUrl).realm(realm)
        .grantType(OAuth2Constants.CLIENT_CREDENTIALS).clientId(clientId).clientSecret(clientSecret)
        .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(poolSize).build()).build();
  }

  /**
   * Add roles to a user based on the keycloak ID.
   * 
   * @param id the keycloak ID of the user. Must be in UUID format
   * @param roles list of roles(Roles enum) to be added. If the list is empty the promise is
   *        completed and future return successfully
   * @return void future
   */

  Future<Void> modifyRoles(String id, List<Roles> roles) {
    Promise<Void> p = Promise.promise();
    if (roles.size() == 0) {
      p.complete();
      return p.future();
    }

    RealmResource realmResource = null;
    UsersResource usersResource = null;
    List<RoleRepresentation> rolesToAssign = new ArrayList<RoleRepresentation>();

    try {
      realmResource = keycloak.realm(realm);
      RolesResource roleResource = realmResource.roles();
      usersResource = realmResource.users();
      rolesToAssign =
          roles.stream().map(role -> roleResource.get(role.name().toLowerCase()).toRepresentation())
              .collect(Collectors.toList());
    } catch (ProcessingException e) {
      p.fail("Error in Keycloak connection : " + e.getMessage());
      return p.future();
    } catch (NotFoundException e) {
      p.fail("Realm/roles may not exist");
      return p.future();
    } catch (Exception e) {
      p.fail(e.getMessage());
      return p.future();
    }

    try {
      UserResource u = usersResource.get(id);
      u.roles().realmLevel().add(rolesToAssign);
      p.complete();
    } catch (NotFoundException e) {
      // TODO log that the user did not exist on KC
      p.fail("User not found");
    } catch (Exception e) {
      p.fail(e.getMessage());
    }

    return p.future();
  }

  /**
   * Add provider role for users. Reuses the modifyRoles method.
   * 
   * @param ids list of keycloak IDs of the users. Must be in UUID format
   * @return void future
   */
  public Future<Void> approveProvider(List<String> ids) {

    Promise<Void> p = Promise.promise();

    /*
     * call modifyRoles for all IDs, return list of futures. CompositeFuture cannot accept a list of
     * parameterized futures, so use raw type and suppress warning
     */
    @SuppressWarnings("rawtypes")
    List<Future> futures = ids.stream().map(id -> modifyRoles(id, List.of(Roles.PROVIDER)))
        .collect(Collectors.toList());

    CompositeFuture.all(futures).onSuccess(success -> {
      p.complete();
    }).onFailure(err -> p.fail(err.getMessage()));

    return p.future();
  }

  /**
   * Get email ID in lowercase of a user based on the keycloak ID. If the user does not exist, an
   * empty string is returned.
   * 
   * @param id the keycloak ID of the user. Must be in UUID format
   * @return a future of String type containing the email address
   */

  public Future<String> getEmailId(String id) {
    Promise<String> p = Promise.promise();
    RealmResource realmResource = null;
    UsersResource usersResource = null;

    try {
      realmResource = keycloak.realm(realm);
      usersResource = realmResource.users();
      usersResource.count();
    } catch (ProcessingException e) {
      p.fail("Error in Keycloak connection : " + e.getMessage());
      return p.future();
    } catch (NotFoundException e) {
      p.fail("Realm may not exist");
      return p.future();
    } catch (Exception e) {
      p.fail(e.getMessage());
      return p.future();
    }

    try {
      UserResource u = usersResource.get(id);
      String email = u.toRepresentation().getEmail();
      p.complete(email);
    } catch (NotFoundException e) {
      // TODO log that the user did not exist on KC
      p.complete("");
    } catch (Exception e) {
      p.fail(e.getMessage());
    }

    return p.future();
  }

  /**
   * Get email and name details for a list of users in JSON format in a map. If the user is not
   * found, an empty JSON object is used as the value.
   * 
   * @param ids List of String UUIDs of keycloak IDs
   * @return map of keycloak ID to JSON object with name, email
   */
  public Future<Map<String, JsonObject>> getDetails(List<String> ids) {
    Promise<Map<String, JsonObject>> p = Promise.promise();
    RealmResource realmResource = null;
    UsersResource usersResource = null;

    Map<String, JsonObject> map = new HashMap<String, JsonObject>();
    try {
      realmResource = keycloak.realm(realm);
      usersResource = realmResource.users();
      usersResource.count();
    } catch (ProcessingException e) {
      p.fail("Error in Keycloak connection : " + e.getMessage());
      return p.future();
    } catch (NotFoundException e) {
      p.fail("Realm may not exist");
      return p.future();
    } catch (Exception e) {
      p.fail(e.getMessage());
      return p.future();
    }

    for (String id : ids) {
      JsonObject j = new JsonObject();
      try {
        UserRepresentation u = usersResource.get(id).toRepresentation();
        j.put("email", u.getEmail());
        j.put("name",
            new JsonObject().put("firstName", u.getFirstName()).put("lastName", u.getLastName()));
      } catch (NotFoundException e) {
        // TODO log that the user did not exist on KC
        // put empty JSON object
      } catch (Exception e) {
        p.fail(e.getMessage());
      }
      map.put(id, j);
    }

    p.complete(map);
    return p.future();
  }

  /**
   * \ Find a user on Keycloak by email address. If the user is found, the name, keycloak ID and
   * email address is sent in a JSON object. Else, an empty JSON object is sent. Note that a user
   * may exist on Keycloak but may not have a user profile.
   * 
   * @param email The email address of the user to be found
   * @return
   */
  public Future<JsonObject> findUserByEmail(String email) {
    Promise<JsonObject> p = Promise.promise();
    RealmResource realmResource = null;
    UsersResource usersResource = null;

    try {
      realmResource = keycloak.realm(realm);
      usersResource = realmResource.users();
      usersResource.count();
    } catch (ProcessingException e) {
      p.fail("Error in Keycloak connection : " + e.getMessage());
      return p.future();
    } catch (NotFoundException e) {
      p.fail("Realm may not exist");
      return p.future();
    } catch (Exception e) {
      p.fail(e.getMessage());
      return p.future();
    }

    JsonObject res = new JsonObject();
    /*
     * Since in the realm we configure email as username, we can directly search for the user name
     */
    List<UserRepresentation> users = usersResource.search(email);

    /*
     * TODO: we have to check if the email is exactly equal to what Keycloak returns because the
     * clients uses fuzzy search. admin-client v11 has a search function with exact search. Upgrade
     * to this version.
     */
    if (users.size() == 0 || !users.get(0).getEmail().equals(email)) {
      p.complete(res);
      return p.future();
    }

    UserRepresentation u = users.get(0);
    res.put("keycloakId", u.getId());
    res.put("email", u.getEmail());
    res.put("name",
        new JsonObject().put("firstName", u.getFirstName()).put("lastName", u.getLastName()));
    p.complete(res);
    return p.future();
  }
}
