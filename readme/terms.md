## Roles

The AAA server can be used to acquire and manage roles. These roles can be used to perform particular operations throughout the DX.

### COS Admin

The COS admin is in charge of many COS-related operations at the top level across the various DX components. Some of the tasks that can be only performed by the COS Admin at the AAA server are:

- Registering new resource servers
- Registering new APDs

#### Role Assignment

- A COS **may only have one user with the COS Admin role**. 
- The user who has that role is determined by the `cosAdminUserId` config option in the AAA configuration, which is the Keycloak user ID of the user. When a user calls an API on the AAA server, the user ID is checked against `cosAdminUserId` to see if the user needs to be regarded as the COS Admin.

### RS Admin

A user with RS Admin role can do operations on the resource servers they have been made admins for across the various DX components. Some of the tasks that can be only performed by an RS Admin at the AAA server are:

- Approve/reject a provider registration to their resource server

#### Role Assignment

- Any user who has registered on the DX Keycloak instance can obtain the RS Admin role.
- A user obtains the RS Admin role for a particular resource server when the COS Admin registers a new resource server on the AAA serer and assigns them as the owner. 
- A user may have the RS Admin role for multiple resource servers.

### Provider

A user with the Provider role is in charge of managing resources owned by them. In general, they deal with:

- registration of resource groups and resource items on the DX catalogue
- onboarding of data onto resource server
- managing access to resources

#### Role Assignment

- Any user who has registered on the DX Keycloak instance can register for the Provider role.
- The Provider role is always scoped to a particular resource server - the resource server on which the provider intends to host their resources.
- A user can register for the Provider role for multiple resource servers using the Add Roles API on the AAA server.
    - **The registration must be approved by the concerned RS Admin for the user to actually be granted the Provider role scoped to that resource server**

### Consumer

A user with the Consumer role can access data provided that they have the correct permissions to.

#### Role Assignment

- Any user who has registered on the DX Keycloak instance can register for the Consumer role.
- The Consumer role is always scoped to a particular resource server - the resource server on which resources that the consumer is interested in consuming.
- A user can register for the Consumer role for multiple resource servers using the Add Roles API on the AAA server.
- When a new resource server is registered onto the AAA server, all users with an existing Consumer role automatically get a new Consumer role scoped to the new resource server.

### Delegate

A user with the Delegate role can perform tasks (scoped to a particular resource server) on behalf of users with Consumer/Provider roles. For more information, refer to the [Delegations](#delegations) section.

#### Role Assignment

- Any user who has registered on the DX Keycloak instance can obtain for the Delegate role.
- A user obtains the Delegate role for a particular resource server when a user with Provider or Consumer role creates a new delegation using the Create Delegation API on the AAA server. 
- A user can lose the Delegate role if all delegations associated with them are deleted by the Provider/Consumer users who created them.

### Trustee

A user with the Trustee role can perform tasks on APDs that they have been made owners of.  

#### Role Assignment

- Any user who has registered on the DX Keycloak instance can obtain the Trustee role.
- A user obtains the Trustee role for a particular APD when the COS Admin registers a new APD on the AAA serer and assigns them as the owner. 
- A user may have the Trustee role for multiple APDs.
- A user can lose the Trustee role if all APDs associated with them are set to inactive state by the COS Admin.

## Tokens

The DX AAA server generates JWT tokens as a result of a successful [Create Token API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datakaveri/iudx-aaa-server/main/docs/openapi.yaml#tag/Token-APIs/operation/post-auth-v1-token) call. The tokens are scoped to a particular role and to an item, where the item is not necessarily a resource item. 

The DX ecosystem categorises the tokens issued by the the AAA server into 2 broad categories described below.

### Identity token/Open token

This token is mostly used to identify a user to a server in the DX ecosystem. Resource servers may also use this token to grant access to resources that are considered 'open' or 'unrestricted'. The token does not contain any resource information.

### Access token

This token is generated as a result of a successful request for access to a resource item. 

-----------------------------------------

For more information regarding:

- what kind of tokens a particular role can obtain
- the checks performed before a particular token is issued
- the structure of the JWT token

please refer to the [Create Token API documentation](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datakaveri/iudx-aaa-server/main/docs/openapi.yaml#tag/Token-APIs/operation/post-auth-v1-token).

## Client credentials

Most APIs on the DX AAA server require authorization in the form of a DX Keycloak token. Since obtaining the Keycloak token can only be done through a browser interaction, an alternative authorization mode, **client credentials** are supported for AAA APIs that benefit from programmatic access (for example, the Create Token API).

Client credentials comprise of a **client ID** and a **client secret** - they are added as header parameters for those AAA APIs that support client credentials authorization. 

The credentials can be obtained using the [Get Default Client Credentials API](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datakaveri/iudx-aaa-server/main/docs/openapi.yaml#tag/User-APIs/operation/get-auth-v1-user-clientcredentials) once a user has a valid role on the AAA server. The credentials can also be reset in case they are compromised.

For more information on which APIs support client credential authorization, please refer to the API documentation.

## Delegations

Users having the Consumer or Provider role can choose to delegate tasks that they need to perform across the DX ecosystem to other users. The delegations can be created, listed and deleted using the [Delegation APIs](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/datakaveri/iudx-aaa-server/main/docs/openapi.yaml#tag/Delegation-APIs). The users who have been assigned a delegation to them obtain the [Delegate](#delegate) role.

Currently delegations for Consumer role and Provider role can be created. Users who have been assigned these delegations are referred to as **consumer-delegates** and **provider-delegates** respectively in the DX ecosystem. The delegate cannot perform any operation on the AAA server other than obtaining tokens scoped to the Delegate role. Other servers in the DX ecosystem can independently choose what operations a provider-delegate and consumer-delegate can perform.

Delegations are always scoped to a role and a resource server. Ideally this means that the delegate is limited to the particular role for operations or data related to that resource server. For example, a delegation created for the Consumer role on resource server `rs.example.com` limits the delegate to doing Consumer role related operations for the resource server `rs.example.com` and accessing data with Consumer role related permissions hosted on `rs.example.com`.

## Resource Servers

In the DX ecosystem, resource servers host data that are supplied by providers and allow data access to consumers. A resource server needs to be registered on the AAA server by the COS Admin to allow the owner of the resource server (the user with the [RS Admin role](#rs-admin)) to approve/reject users who intend to provide data to the server.

## Access Policy Domain (APD)

In order for the DX to support a variety of information sources and decision schemes for resource access we introduced the concept of an APD. Integrating all these into the AAA server is difficult, hence this component allows the AAA server to offload the decision to them. 

APDs are registered and managed by the COS Admin on the AAA server.