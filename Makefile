.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help env up down reset logs ps test-e2e

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
