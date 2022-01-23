#!/bin/bash

files_present=$(ls ./src/main/resources/db/migration | wc -l)
new_version=$(echo "$files_present+1" | bc)
cp /home/ubuntu/configs/aaa-Add_Integration_Test_data.sql ./src/main/resources/db/migration/V${new_version}__Add_Integration_Test_data.sql
mvn flyway:migrate -Dflyway.configFiles=/home/ubuntu/configs/aaa-flyway.conf
mvn flyway:clean -Dflyway.configFiles=/home/ubuntu/configs/aaa-flyway.conf