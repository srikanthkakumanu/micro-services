.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help env up down reset logs ps test-e2e k8s-up k8s-down test-e2e-k8s

help: ## Show the available targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

env: ## Create .env with generated dev secrets if it does not exist
	@./scripts/init-env.sh

up: env ## Build and start the whole stack, waiting until it is healthy
	$(COMPOSE) up --build --detach --wait

down: ## Stop the stack, keeping data and secrets
	$(COMPOSE) down

reset: ## Stop the stack and delete volumes and generated secrets
	$(COMPOSE) down --volumes --remove-orphans
	rm -f .env

logs: ## Follow the logs of all services
	$(COMPOSE) logs --follow

ps: ## Show service status
	$(COMPOSE) ps

test-e2e: ## Run the end-to-end suite against the running stack
	./gradlew :e2e:e2eTest

k8s-up: ## Deploy to the local Kubernetes cluster (namespace identity-dev)
	./scripts/k8s-up.sh

k8s-down: ## Remove the Kubernetes deployment
	./scripts/k8s-up.sh --delete

test-e2e-k8s: ## Run the end-to-end suite against the Kubernetes deployment
	E2E_GATEWAY_URL=http://localhost:30211 E2E_USER_SERVICE_URL=http://localhost:30121 \
	E2E_AUTH_SERVICE_URL=http://localhost:30141 E2E_MAILPIT_URL=http://localhost:30025 \
	E2E_VAULT_URL=http://localhost:30200 E2E_CONFIG_SERVER_URL=http://localhost:30311 \
	E2E_ISSUER=http://localhost:30080/realms/platform E2E_PROFILE=k8s \
	E2E_INTERNAL_EXEC="kubectl -n identity-dev exec deploy/api-gateway --" \
	./gradlew :e2e:e2eTest
