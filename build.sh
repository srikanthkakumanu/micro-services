#!/bin/bash

clear
echo "----------  Gradle: clean & build started..  ----------"

echo "----------  eureka-discovery: clean and build  ----------"
cd eureka-discovery && gradle clean build
cd ..

#echo "----------  user-service: clean and build  ----------"
#cd user-service && gradle clean build
#cd ..

echo "----------  books-service: clean and build  ----------"
cd books-service && gradle clean build
cd ..

echo "----------  api-gateway: clean and build  ----------"
cd api-gateway && gradle clean build
cd ..

echo "----------  Gradle: clean & build completed..  ----------"

echo "----------  Docker: Image preparation started...  ----------"
docker compose build
echo "----------  Docker: Image preparation completed...  ----------"

