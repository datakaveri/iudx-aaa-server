![IUDX](./readme/images/iudx.png)
# India Urban Data eXchange (IUDX) Authentication, Authorization, and Accounting (AAA) Server

IUDX-AAA is the Authentication, Authorization, and Accounting server for accessing [IUDX](https://www.iudx.org.in) services.

<p align="center">
<img src="./readme/images/aaa_overview.png">
</p>

## Get Started

### Prerequisite - Make configuration
Make a config file based on the template in `./configs/config-example.json` 
- Generate a certificate using Lets Encrypt or other methods
- Make a Java Keystore File and mention its path and password in the appropriate sections
- Modify the database url and associated credentials in the appropriate sections
- Set up the database using Flyway

#### Flyway Database setup

Flyway is used to manage the database schema and handle migrations. The migration files are located at [src/main/resources/db/migrations](src/main/resources/db/migrations). The following pre-requisites are needed before running `flyway`:
1. An admin user - a database user who has create schema/table privileges for the database. It can be the super user.
2. An auth user - a database user with no privileges; this is the database user that will be configured to make queries from the server 
(e.g. `CREATE USER auth WITH PASSWORD 'randompassword';`)

[flyway.conf](flyway.conf) must be updated with the required data. 
`flyway.url` - the database connection URL
`flyway.user` - the username of the admin user
`flyway.password` - the password of the admin user
`flyway.schemas` - the name of the schema under which the tables are created
`flyway.placeholders.authUser` - the username of the auth user

Please refer [here](https://flywaydb.org/documentation/configuration/parameters/) for more information about Flyway config parameters.

After this, the `info` command can be run to test the config. Then, the `migrate` command can be run to set up the database. At the `/iudx-aaa-server` directory, run

```
mvn flyway:info -Dflyway.configFiles=flyway.conf
mvn flyway:migrate -Dflyway.configFiles=flyway.conf
```

### Docker based
1. Install docker and docker-compose
2. Clone this repo
3. Build the images 
   ` ./docker/build.sh`
4. Modify the `docker-compose.yml` file to map the config file you just created
5. Start the server in production (prod) or development (dev) mode using docker-compose 
   ` docker-compose up prod `
6. The server will be up in Port 8443 is SSL is true or in Port 8080 if SSL is false.

### Maven based
1. Install java 13 and maven
2. Use the maven exec plugin based starter to start the server 
   `mvn clean compile exec:java@aaa-server`
3. The server will be up in Port 8443 is SSL is true or in Port 8080 if SSL is false.

### Testing

### TDD based Unit test flow
1. Run the server through either docker, maven or redeployer
2. Run the all unit tests for a Service
   `mvn -Dtest=TIPServiceTest test` 
3. Run the unit tests for a method in a Service
   `mvn -Dtest=TIPServiceTest#validateTokenSuccess test` 

### Unit test and Reports
1. Run the server through either docker, maven or redeployer
2. Run the unit tests and generate a surefire report 
   `mvn clean test-compile surefire:test surefire-report:report`
3. Reports are stored in `./target/`

## Contributing
We follow Git Merge based workflow 
1. Fork this repo
2. Create a new feature branch in your fork. Multiple features must have a hyphen separated name, or refer to a milestone name as mentioned in Github -> Projects 
3. Commit to your fork and raise a Pull Request with upstream

## License
[MIT](./LICENSE)
