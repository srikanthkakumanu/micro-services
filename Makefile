.DEFAULT_GOAL := help
COMPOSE := docker compose
# Which configuration the stack runs with: dev, qa or prod.
ENV ?= dev

.PHONY: help env up start stop down restart status reset logs ps test-e2e k8s-up k8s-down test-e2e-k8s

help: ## Show the available targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-13s %s\n", $$1, $$2}'

env: ## Create .env for ENV (default dev) with generated secrets if it does not exist
	@./scripts/init-env.sh $(ENV)

up: ## Build the images and start the platform in order (ENV=dev|qa|prod)
	./scripts/start.sh $(ENV) --build

start: ## Start the platform in order without rebuilding (S="service ..." for some only)
	./scripts/start.sh $(S)

stop: ## Stop the platform gracefully and remove the containers, keeping data (S="service ..." for some only)
	./scripts/stop.sh $(S)

down: stop ## Same as stop

restart: ## Restart gracefully (S="service ..." for some only, BUILD=1 to rebuild)
	./scripts/restart.sh $(if $(BUILD),--build) $(S)

status: ## Show what is running and whether the gateway can reach the services
	@./scripts/status.sh

reset: ## Stop the platform and delete data volumes and generated secrets
	./scripts/stop.sh --reset --yes

logs: ## Follow the logs (S="service ..." for some only)
	$(COMPOSE) logs --follow $(S)

ps: ## Show container status
	$(COMPOSE) ps --all

test-e2e: ## Run the end-to-end suite against the running stack
	./gradlew :e2e:e2eTest

k8s-up: ## Deploy the dev overlay to the local Kubernetes cluster (namespace identity-dev)
	./scripts/k8s-up.sh dev

k8s-down: ## Remove the dev Kubernetes deployment
	./scripts/k8s-up.sh dev --delete

test-e2e-k8s: ## Run the end-to-end suite against the Kubernetes deployment
	E2E_GATEWAY_URL=http://localhost:30211 E2E_USER_SERVICE_URL=http://localhost:30121 \
	E2E_AUTH_SERVICE_URL=http://localhost:30141 E2E_BOOKS_SERVICE_URL=http://localhost:30151 \
	E2E_MAILPIT_URL=http://localhost:30025 \
	E2E_VAULT_URL=http://localhost:30200 E2E_CONFIG_SERVER_URL=http://localhost:30311 \
	E2E_ISSUER=http://localhost:30080/realms/platform E2E_PROFILE=dev \
	E2E_INTERNAL_EXEC="kubectl -n identity-dev exec deploy/api-gateway --" \
	./gradlew :e2e:e2eTest
