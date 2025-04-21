#!/bin/bash

echo "Gradle: clean & build started.."

#echo "books-service: clean and build"
#cd books-service && gradle clean build
#cd ..

echo "eureka-discovery: clean and build"
cd eureka-discovery && gradle clean build
cd ..

#echo "todo-service: clean and build"
#cd todo-service && gradle clean build
#cd ..

echo "user-service: clean and build"
cd user-service && gradle clean build
cd ..

echo "video-service: clean and build"
cd video-service && gradle clean build
cd ..

echo "Gradle: clean & build completed.."

echo "Docker: Image preparation started..."
docker compose build
echo "Docker: Image preparation completed..."

