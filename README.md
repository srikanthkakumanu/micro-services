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

# -- Root DB --
vault kv put secret/data/db/root/dev user=root password=root profile=dev
echo "Secret 'secret/data/db/root/dev' created."
vault kv put secret/data/db/root/qa user=root password=root profile=qa
echo "Secret 'secret/data/db/root/qa' created."
vault kv put secret/data/db/root/prod user=root password=root profile=prod
echo "Secret 'secret/data/db/root/prod' created."

# -- Microservices & DB --
# User Micro Service = DB
vault kv put secret/data/db/userdb/dev user=theuser password=theuser flw-user=useradmin flw-password=useradmin db-name=userdb profile=dev
echo "Secret 'secret/data/db/userdb/dev' created."
vault kv put secret/data/db/userdb/qa user=theuser password=theuser flw-user=useradmin flw-password=useradmin db-name=userdb profile=qa
echo "Secret 'secret/data/db/userdb/qa' created."
vault kv put secret/data/db/userdb/prod user=theuser password=theuser flw-user=useradmin flw-password=useradmin db-name=userdb profile=prod
echo "Secret 'secret/data/db/userdb/prod' created."

# User Micro Service - Security & Authentication
vault kv put secret/data/ms/security/auth/dev auth-user=theuser auth-password=theuser profile=dev
echo "Secret 'secret/data/ms/security/auth/dev' created."
vault kv put secret/data/ms/security/auth/qa auth-user=theuser auth-password=theuser profile=qa
echo "Secret 'secret/data/ms/security/auth/qa' created."
vault kv put secret/data/ms/security/auth/prod auth-user=theuser auth-password=theuser profile=prod
echo "Secret 'secret/data/ms/security/auth/prod' created."

# Books Micro Service - DB
vault kv put secret/data/db/booksdb/dev user=theuser password=theuser flw-user=bookadmin flw-password=bookadmin db-name=booksdb profile=dev
echo "Secret 'secret/data/db/booksdb/dev' created."
vault kv put secret/data/db/booksdb/qa user=theuser password=theuser flw-user=bookadmin flw-password=bookadmin db-name=booksdb profile=qa
echo "Secret 'secret/data/db/booksdb/qa' created."
vault kv put secret/data/db/booksdb/prod user=theuser password=theuser flw-user=bookadmin flw-password=bookadmin db-name=booksdb profile=prod
echo "Secret 'secret/data/db/booksdb/prod' created."

# -- API --
vault kv put secret/data/api/keys/dev gh-user=<your GitHub username> gh-password=<your GitHub password> api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=dev
echo "Secret 'secret/data/api/keys/dev' created."
vault kv put secret/data/api/keys/qa gh-user=<your GitHub username> gh-password=<your GitHub password> api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=qa
echo "Secret 'secret/data/api/keys/qa' created."
vault kv put secret/data/api/keys/prod gh-user=<your GitHub username> gh-password=<your GitHub password> api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=prod
echo "Secret 'secret/data/api/keys/prod' created."


# Verify secrets have been written
echo "Verifying secrets..."
vault kv get secret/data/db/root/dev
vault kv get secret/data/db/root/qa
vault kv get secret/data/db/root/prod

vault kv get secret/data/db/userdb/dev
vault kv get secret/data/db/userdb/qa
vault kv get secret/data/db/userdb/prod

vault kv get secret/data/db/booksdb/dev
vault kv get secret/data/db/booksdb/qa
vault kv get secret/data/db/booksdb/prod

vault kv get secret/data/api/keys/dev
vault kv get secret/data/api/keys/qa
vault kv get secret/data/api/keys/prod

echo "Secret initialization complete."
```
