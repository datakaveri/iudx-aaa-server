# Setup

## Steps
- Setup the key used to sign JWT tokens
- Set up the database using Flyway
- Set up Keycloak
- Create COS Admin
- ImmuDB setup

### JWT signing key setup

The Current Implementation of JWT in iudx-aaa-server is based on Vert.x `vertx-auth-jwt` library.  The Vert.x JWT implementation or any other  Vertx security  implementations generally requires a creation/implementation of security interface AuthenticationProvider, In JWT case it is JWTAuth as Authentication Provider

There are multiple types of signature methods used for signing JWT, and each of them either requires buffer or PKI or certificates, or jks for instantiating the Authentication Provider in Vertx.

The current implementation is based on asymmetric key algorithm **ECDSA** (Elliptic Curve Digital Signature Algorithm), and the signature method is **ES256**.

The Authentication Provider looks for aliases in the provided Keystore to verify and sign the generated JWT. The keystore and keypair should also be generated and signed using same algorithm which is required to sign and verify the JWT. For Signature algorithm ES256, the keystore alias is ES256. 

The Keytool command to generate ECDSA keystore keypair is:

```
 keytool -genkeypair -keystore keystore-ec.jks -storetype jks -storepass secret -keyalg EC -alias ES256 -keypass secret -sigalg SHA256withECDSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360 -deststoretype pkcs12
```

The keystore path and the keystore password should then be added to the server config.

### Flyway Database setup

Flyway is used to manage the database schema and handle migrations. The migration files are located at [src/main/resources/db/migrations](src/main/resources/db/migrations). The following pre-requisites are needed before running `flyway`:
1. An admin user - a database user who has create schema/table privileges for the database. It can be the super user.
2. An auth user - a database user with no privileges; this is the database user that will be configured to make queries from the server 
(e.g. `CREATE USER auth WITH PASSWORD 'randompassword';`)

[flyway.conf](flyway.conf) must be updated with the required data. 
* `flyway.url` - the database connection URL
* `flyway.user` - the username of the admin user
* `flyway.password` - the password of the admin user
* `flyway.schemas` - the name of the schema under which the tables are created
* `flyway.placeholders.authUser` - the username of the auth user

Please refer [here](https://flywaydb.org/documentation/configuration/parameters/) for more information about Flyway config parameters.

After this, the `info` command can be run to test the config. Then, the `migrate` command can be run to set up the database. At the `/iudx-aaa-server` directory, run

```
mvn flyway:info -Dflyway.configFiles=flyway.conf
mvn flyway:migrate -Dflyway.configFiles=flyway.conf
```

### Keycloak setup

The AAA server uses [Keycloak](https://www.keycloak.org/about.html) to manage user identity. Please refer [here](https://www.keycloak.org/docs/latest/server_admin/#core-concepts-and-terms) to become familiar with Keycloak terminology.

#### Realm Creation

- A new realm must be configured. Follow the steps outlined [here](https://www.keycloak.org/docs/latest/server_admin/#_configuring-realms) to create a new realm.

- Once the new realm has been created, `Email as username` and `Login with email` must be configured in the `Login` tab of the `Realm settings` panel for the new realm


#### Auth Server client creation

The AAA server requires a client to be configured that would allow the server to interact with Keycloak. The client would be able to search for users on the configured Keycloak realm, as well as validate OIDC tokens issued by Keycloak from that realm.

- Follow the steps outlined [here](https://www.keycloak.org/docs/latest/server_admin/#proc-creating-oidc-client_server_administration_guide) to create a new client. Ensure that:
    - In Capability config `Client authentication` is turned on, `Authorization` is off, and in `Authentication flow`, **only** `Service accounts roles` is turned on
- Once the client has been created successfully, fetch the client secret of the client by following the steps outlined [here](https://www.keycloak.org/docs/latest/server_admin/#_client-credentials). The client ID is the name of the client itself.
- This client must have the capability to search for users and realms (In the `Service account roles` tab, click `Assign Role` -> choose `Filter by clients` -> search for `realm-management` -> select `view-users` and click `Assign`)


#### COS Admin User creation

The COS Admin user can perform certain tasks in the COS. The Keycloak user ID of this user must be placed in the AAA config in order for the server to recognize that the particular user has the role of COS Admin.

- Create a user on Keycloak by following the steps [here](https://www.keycloak.org/docs/latest/server_admin/#proc-creating-user_server_administration_guide). Copy the user ID of the user once created.
- Set a **permanent** password for the user by following the steps [here](https://www.keycloak.org/docs/latest/server_admin/#ref-user-credentials_server_administration_guide).

The copied user ID will later be used in the config.

### ImmuDB setup

ImmuDB is used to record audit logs of all operations done on the AAA server.

- Create a table in ImmuDB with schema as specified [here](https://github.com/datakaveri/dx-resource-server/blob/5.5.0/src/main/resources/db/migration/V4_0__Add-tables-in-postgres-for-metering.sql#L41-L50)


