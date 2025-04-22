package iudx.aaa.server.apiserver;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import iudx.aaa.server.apiserver.models.Roles;
import iudx.aaa.server.apiserver.models.User;

import java.util.List;
import java.util.Set;

import static iudx.aaa.server.apiserver.util.Constants.USER;

public class RoleAuthorisationHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.next();
    }

    public void validateRole(RoutingContext routingContext, Set<Roles> requestedRoles) {
        User user = routingContext.get(USER);

        if (requestedRoles.isEmpty()) {
            System.out.println("No roles requested, allowing access");
            routingContext.next();
            return;
        }

        if (user.getRoles().stream().noneMatch(requestedRoles::contains)) {
            System.out.println("Role not authorised");
            routingContext.fail(403);
            return;
        }
        System.out.println("Role authorisation successful");
        routingContext.next();
    }
}
