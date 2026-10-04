#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Record the start time using the shell's built-in SECONDS variable
start_time=$SECONDS
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace_dir="$(cd "$script_dir/.." && pwd)"

clear
echo "----------  Gradle: clean & build started..  ----------"

# Using a function to avoid repetition and improve readability
build_service() {
  local service_name=$1
  local service_dir="$workspace_dir/$service_name"
  echo "----------  $service_name: clean and build  ----------"
  # Run in a subshell to avoid changing the script's main directory
  (cd "$service_dir" && ./gradlew clean build --build-cache)
}

build_service "eureka-discovery"
build_service "api-gateway"
build_service "cloud-config-service"
build_service "user-service"
build_service "auth-service"
build_service "books-service"
build_service "video-service"
build_service "todo-service"

echo "----------  Gradle: clean & build completed..  ----------"

echo "----------  Docker: Image preparation started...  ----------"
# Run
(cd "$script_dir" && docker buildx bake -f docker-bake.hcl)
# docker buildx bake -f compose.yml
echo "----------  Docker: Image preparation completed...  ----------"

# Calculate the total duration
duration=$((SECONDS - start_time))

echo
echo "======================================================"
echo "Build script finished successfully!"
printf "Total execution time: %d minutes and %d seconds.\n" $((duration / 60)) $((duration % 60))
echo "======================================================"
