#!/bin/bash

files_present=$(ls ./src/main/resources/db/migration | wc -l)
new_version=$(echo "$files_present+1" | bc)
cp /home/ubuntu/configs/3.5.0/aaa-Add_Integration_Test_data.sql ./src/main/resources/db/migration/V${new_version}__Add_Integration_Test_data.sql
mvn flyway:migrate -Dflyway.configFiles=/home/ubuntu/configs/3.5.0/aaa-flyway.conf