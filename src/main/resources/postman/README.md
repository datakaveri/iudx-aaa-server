# Using the Postman Collection

All APIs can be accessed with the given Postman collection with the environment file. For production, the values of the environment variables must be:

* `AUTH_ENDPOINT` : `https://authorization.iudx.org.in`
* `KEYCLOAK_ENDPOINT` : `https://keycloak.iudx.org.in`
* `KEYCLOAK_REALM` : `iudx`

## OIDC Authentication

As the auth server uses OIDC token-based authentication, an OIDC JWT token is required to be sent along with most requests. Postman eases the process of obtaining said tokens by providing the `Authorization` tab in each request. The details needed to configure getting tokens have already been filled out. Click the `Get New Access Token` button, after which you will be prompted to enter the email address and password you have registered with. If the credentials are correct, a token is returned. Click `Use Token` to use that particular token for the request.

As these OIDC tokens are short lived, in case you get a `401 Unauthorized` error from the auth server, you may re-click the `Get New Access Token` button to get a new token.

For more information, please see the Postman documentation for [OAuth 2.0 authorization](https://learning.postman.com/docs/sending-requests/authorization/#oauth-20) and also [Authorization Code Grant](https://learning.postman.com/docs/sending-requests/authorization/#authorization-code) which is the OAuth 2.0 grant type used by the server.
