# Configuration

Please refer to [config-example.json](/configs/config-example.json) for a sample configuration file.

## Main Values

| Value  | Type | Example| Description |
| -------|----- | -------|----- |
| `version` | Float | |The version number  |
| `zookeepers`  | JSON Array of strings | |? |
| `clusterId` | String | ? |
| `options` | JSON Object | A collection of JSON objects that verticle configs can choose to use using the `required` array  |
| `modules` | JSON Array of JSON objects | Verticle-specific configurations

## `options`

`options` comprises of a set of configurations organized in JSON objects that verticles can choose to add to their config. Each verticle config has a `required` array, where they can add a list of `options`. These configurations will get merged into the verticle config before the config is deployed. This mechanism avoid repeating the same config across each verticle.

| Value  | Type | Example | Description |
| -------|----- | --------|---- |
| `postgresOptions` | JSON Object | {...}| Postgres connection related options |
| `commonOptions`  | JSON Object | {...}|Common or server-specific options |
| `keycloakOptions` | JSON Object| {...}|Keycloak related options |
| `jwtKeystoreOptions` | JSON Object | {...}|JWT-signing keystore related options|

### `postgresOptions`

| Value  | Type | Example |Description |
| -------|----- | -------|----- |
| `databaseIP` | String | `database.iudx.io`/`192.186.0.1` |The IP/hostname of the Postgres instance  |
| `databasePort`  | String | `5432` |The port number of the Postgres instance |
| `databaseName` | String | `database` |The name of the DB being used in the Postgres instance |
| `databaseSchema` | String | `schema` |The name of the DB schema being used in the Postgres instance|
| `databaseName` | String | `name` |The name of the DB user used to connect to the Postgres instance |
| `databasePassword` | String | `password` |The password of the DB user used to connect to the Postgres instance |

### `commonOptions`

| Value  | Type | Example |Description |
| -------|----- | --------|---- |
| `cosDomain` | String | |The domain name used by the COS |
| `cosAdminUserId`  | String | |The DX keycloak user ID of the COS Admin user |

### `keycloakOptions`
| Value  | Type | Example |Description |
| -------|----- | -------|----- |
| `keycloakRealm` | String | `realm` | The Keycloak realm configured for the DX |
| `keycloakUrl`  | String | `https://keycloak.dx.org.in/auth` | The Keycloak server URL in the format `{{protocol}}://{{keycloakHost}}:{{keycloakPort}}/auth`  |
| `keycloakAdminClientId` | String | `keycloak-admin` | The client ID of the Keycloak client created for the AAA server |
| `keycloakAdminClientSecret` | String | `<UUID/Base64 string 32 characters long>` | The client secret for the Keycloak client created for the AAA server|
| `keycloakAdminPoolSize` | String | `10` | The number of API clients that can be created in a client pool to connect with Keycloak and make requests|
| `keycloakJwtLeeway` | Integer | `90` | The leeway for the Keycloak JWT token in seconds. The leeway accounts for clock skew on the AAA server when checking if a token has expired (`exp`) or when a token is valid (`iat`/`nbf`)|

### `jwtKeystoreOptions`

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `keystorePath` | String | `configs/keystore.jks`  | The path to the keystore used to sign AAA tokens |
| `keystorePassword`  | String | `password`| The password of the keystore |

## `modules`

`modules` is a JSON array comprising of all the individual verticle configs.

### Options common to all verticle configs

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `id` | String | `iudx.aaa.server.policy.PolicyVerticle`  | The package name of the verticle to be deployed |
| `verticleInstances`  | Integer | `1`| The number of instances of the verticle to be deployed |
| `required`  | JSON Array | `["postgresOptions"]`| The config blocks from the `options` object to be merged with this verticle's config. See the example config for verticle specific values |
| `poolSize`  | String | `5`| The size of the Postgres DB pool created for this verticle|

### Verticle-specific config options

#### PolicyVerticle

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `catalogueOptions` | JSON Object | {...} | JSON object containing DX catalogue information to fetch data from the catalogue |

##### `catalogueOptions`

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `catServerHost` | String | `api.catalogue.dx.com`  | The DX catalogue URL |
| `catServerPort`  | String | `8080`| The DX catalogue port|
| `catServerBasePath`  | String | `/dx/cat/v1`| The base path used in DX catalogue APIs |

#### RegistrationVerticle

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `serversOmittedFromRevoke` | JSON Array of strings | `["rs.dx.org"]`  | Servers that will not be intimated about token revocation |

#### ApdVerticle

| Value  | Type | Example |Description |
| -------|----- | --------|----- |
| `webClientTimeoutMs` | Integer| `3000`  | Timeout in milliseconds for APD `/verify` API call |

#### AuditingVerticle

| Value  | Type | Example |Description |
| -------|----- | -------|----- |
| `auditingDatabaseIP` | String | `database.iudx.io`/`192.186.0.1` |The IP/hostname of the ImmuDB instance  |
| `auditingDatabasePort`  | String | `5432` |The port number of the ImmuDB instance |
| `auditingDatabaseName` | String | `database` |The name of the DB being used in the ImmuDB instance |
| `auditingDatabaseSchema` | String | `schema` |The name of the DB schema being used in the ImmuDB instance|
| `auditingDatabaseName` | String | `name` |The name of the DB user used to connect to the ImmuDB instance |
| `auditingDatabasePassword` | String | `password` |The password of the DB user used to connect to the ImmuDB instance |
| `auditingDatabaseTableName` | String | `password` |The table in the ImmuDB instance where data will be stored|
| `auditingPoolSize` | Integer| `10` |The number of connections in the pool used to connect to the ImmuDB instance |

#### ApiServerVerticle

| Value  | Type | Example |Description |
| -------|----- | -------|----- |
| `httpPort` | Integer | `8443` |The port at which the AAA server will run|
| `serverTimeoutMs`  | Integer | `5000` | Default timeout for any API call made to the AAA server |
| `corsRegexString` | String | `*` |A regex string used for CORS validation|

