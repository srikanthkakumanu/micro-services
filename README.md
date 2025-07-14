It is a collection of all microservice modules.

To run the Docker compose successfully, Git clone all microservices in this directory.

### List of Microservices

- user-service
- eureka-discovery (service discovery)
- api-gateway (API gateway & Load Balancer)
- todo-service
- video-service
- books-service

## Cloning Micro-service Repositories

To clone all micro-service repositories, you can use the following script.

```bash
sh ./clone_repos.sh
```

## Building Micro-service Jars & Docker Images

To build Jars for micro services and building docker images (uses Docker bake), you can use the following script. *docker-bake.hcl* contains all the microservice declarations.

Note: You comment the unwanted micro service in this script to omit them from the execution.

```bash
sh ./buld.sh
```

## Running micro-services and & DB

You can use the following command: ```docker compose up -d```. It runs the microservices in detached mode.

## Redirect all console logs to file

We can redirect all the console logs to a specific file by using the following command.

```bash

docker compose logs -f -t > console_log.log
```

## Setting Up Vault for Secret Management

1. Run the following commands:

   ``````bash
   chmod +x /home/skakumanu/practice/micro-services/vault/config/init-vault-secrets.sh
   chmod +x /home/skakumanu/practice/micro-services/vault/config/vault-entrypoint.sh
   ``````
2. `docker compose up` - Starts the vault server/service in DEV mode. It also executes *./vault/config/vault-entrypoint.sh* that configures all the required secrets.

Note: *vault-prod.hcl* is used only in PROD environment along with MariaDB as backup storage for secrets. init-vault-secrets.sh file contains sensitive secrets info hence it is added into .gitignore.

Useful Links:

1. [https://myros.net/hashicorp-vault-docker-compose-part1](https://https://myros.net/hashicorp-vault-docker-compose-part1)
2. https://myros.net/hashicorp-vault-docker-compose-part2



***init-vault-secrets.sh** (Sample file)

```bash
#!/bin/sh
# Exit immediately if a command exits with a non-zero status.
set -e

# This script assumes that VAULT_ADDR and VAULT_TOKEN (or VAULT_DEV_ROOT_TOKEN_ID)
# are already set in the environment from which it is called.

echo "Initializing Vault with secrets..."

# Create secrets
vault kv put secret/dev/db/root username=root password=root profile=dev
echo "Secret 'secret/dev/db/root' created."
vault kv put secret/qa/db/root username=root password=root profile=qa
echo "Secret 'secret/qa/db/root' created."
vault kv put secret/prod/db/root username=root password=root profile=prod
echo "Secret 'secret/prod/db/root' created."

vault kv put secret/dev/db username=theuser password=theuser profile=dev
echo "Secret 'secret/dev/db' created."
vault kv put secret/qa/db username=theuser password=theuser profile=qa
echo "Secret 'secret/qa/db' created."
vault kv put secret/prod/db username=theuser password=theuser profile=prod
echo "Secret 'secret/prod/db' created."

vault kv put secret/dev/keys api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=dev
echo "Secret 'secret/dev/keys' created."
vault kv put secret/qa/keys api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=qa
echo "Secret 'secret/qa/keys' created."
vault kv put secret/prod/keys api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=prod
echo "Secret 'secret/prod/keys' created."
vault kv put set/dev/keys/gh gh-username=username gh-password=password profile=dev
echo "Secret 'secret/dev/keys/gh' created."
vault kv put set/dev/keys gh-username=username gh-password=password profile=qa
echo "Secret 'secret/qa/keys/gh' created."
vault kv put set/dev/keys gh-username=username gh-password=password profile=prod
echo "Secret 'secret/prod/keys/gh' created."

# Verify secrets have been written
echo "Verifying secrets..."
vault kv get secret/dev/db/root
vault kv get secret/qa/db/root
vault kv get secret/prod/db/root

vault kv get secret/dev/db
vault kv get secret/qa/db
vault kv get secret/prod/db

vault kv get secret/dev/keys
vault kv get secret/qa/keys
vault kv get secret/prod/keys
echo "Secret initialization complete."

```
