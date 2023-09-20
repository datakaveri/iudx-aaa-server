#!/bin/bash

#cp ./src/test/resources/V1000__Add_Integration_Test_data.sql ./src/main/resources/db/migration/
mvn flyway:migrate -Dflyway.configFiles=/home/ubuntu/configs/aaa-flyway.conf
