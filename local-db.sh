#!/bin/bash

clear
echo "----------  MariaDB: Starting  ----------"
docker run --name mariadb-local -e MARIADB_ROOT_PASSWORD=root 9f3d79eba61e
sleep 7
echo "----------  PHPMyAdmin: Starting  ----------"
docker run -d --name phpmyadmin-local -it phpmyadmin:latest
sleep 7
