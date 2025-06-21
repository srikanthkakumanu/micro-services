#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Record the start time using the shell's built-in SECONDS variable
start_time=$SECONDS

clear
echo "----------  Gradle: clean & build started..  ----------"

# Using a function to avoid repetition and improve readability
build_service() {
  local service_name=$1
  echo "----------  $service_name: clean and build  ----------"
  # Run in a subshell to avoid changing the script's main directory
  (cd "$service_name" && ./gradlew build)
}

build_service "eureka-discovery"
build_service "user-service"
# build_service "books-service" # This service is currently disabled
build_service "api-gateway"

echo "----------  Gradle: clean & build completed..  ----------"

echo "----------  Docker: Image preparation started...  ----------"
# Run
docker buildx bake
# docker buildx bake -f compose.yml
echo "----------  Docker: Image preparation completed...  ----------"

# Calculate the total duration
duration=$((SECONDS - start_time))

echo
echo "======================================================"
echo "Build script finished successfully!"
printf "Total execution time: %d minutes and %d seconds.\n" $((duration / 60)) $((duration % 60))
echo "======================================================"