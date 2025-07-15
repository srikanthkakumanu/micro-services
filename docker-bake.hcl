# This group defines a "default" target.
# Running `docker compose bake` without arguments will build all targets in this group in parallel.
group "default" {
  targets = [
    "eureka-discovery-service",
    "user-service",
    # "books-service",
    "api-gateway",
    # "cloud-config-service"
  ]
}

# A variable for the default version tag, making it easy to update.
variable "VERSION" {
  default = "1.0"
}

# Build definition for the Eureka Discovery Service
target "eureka-discovery-service" {
  # The directory containing the Dockerfile
  context = "./eureka-discovery"
  # The name and tag for the final image
  tags    = ["eureka-discovery-service:latest"]
  # Build arguments passed to the Dockerfile
  args = {
    PROJECT_NAME    = "eureka-discovery-service"
    PROJECT_VERSION = "${VERSION}"
  }
}

# Build definition for the User Service
target "user-service" {
  context = "./user-service"
  tags    = ["user-service:latest"]
  args = {
    PROJECT_NAME    = "user-service"
    PROJECT_VERSION = "${VERSION}"
  }
}

# Build definition for the API Gateway
target "api-gateway" {
  context = "./api-gateway"
  tags    = ["api-gateway:latest"]
  args = {
    PROJECT_NAME    = "api-gateway"
    PROJECT_VERSION = "${VERSION}"
  }
}

# Build definition for the (disabled) Books Service.
# To use it, simply add "books-service" to the "default" group's targets list above.
target "books-service" {
  context = "./books-service"
  tags    = ["books-service:latest"]
  args = {
    PROJECT_NAME    = "books-service"
    PROJECT_VERSION = "${VERSION}"
  }
}

# Build definition for the Cloud Config Server for all microservices.
target "cloud-config-service" {
  # The directory containing the Dockerfile
  context = "./cloud-config-service"
  # The name and tag for the final image
  tags    = ["cloud-config-service:latest"]
  # Build arguments passed to the Dockerfile
  args = {
    PROJECT_NAME    = "cloud-config-service"
    PROJECT_VERSION = "${VERSION}"
  }
}
