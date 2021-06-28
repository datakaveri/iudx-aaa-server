package iudx.aaa.server.registration;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import iudx.aaa.server.apiserver.Roles;
import java.util.ArrayList;
import java.util.List;
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
}
