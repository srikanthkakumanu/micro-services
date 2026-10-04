#!/bin/bash

# This file is applicable only for Prod environments
# This script configures Vault to manage a static user password in PostgreSQL.
# It should be run AFTER `docker compose up` and AFTER the vault container is running.

### Only for Vault Dev environment

#set -e # Exit immediately if a command exits with a non-zero status.
#echo "Note: Run this script only once after starting vault docker container"
#export VAULT_ADDR=http://localhost:8200
#export VAULT_DEV_ROOT_TOKEN_ID=srikanth # This is only for Dev environment

### Only for Vault Prod environment

set -e # Exit immediately if a command exits with a non-zero status.
echo "Note: Run this script only once after starting vault docker container"
export VAULT_ADDR=http://localhost:8200

echo "### Vault Setup for PostgreSQL Static Role ###"
echo

# Step 1: Initialize Vault using JSON output for reliable parsing
# This only needs to be done ONCE. The keys are saved to vault-keys.json.
echo "-> Initializing Vault (if this is the first time)..."
if [ ! -f vault-keys.json ]; then
    echo "vault-keys.json not found. Initializing and saving keys in JSON format."
    vault operator init -key-shares=1 -key-threshold=1 -format=json > vault-keys.json
else
    echo "vault-keys.json found. Skipping initialization."
fi

# Parse keys from JSON file using jq. This is safer than grep/awk.
VAULT_UNSEAL_KEY=$(jq -r .unseal_keys_b64[0] vault-keys.json)
VAULT_ROOT_TOKEN=$(jq -r .root_token vault-keys.json)

# Step 2: Unseal Vault (if it's sealed)
echo "-> Checking Vault seal status..."
SEAL_STATUS=$(vault status -format=json | jq -r .sealed)
if [ "$SEAL_STATUS" = "true" ]; then
  echo "Vault is sealed. Unsealing..."
  vault operator unseal "$VAULT_UNSEAL_KEY" > /dev/null
  echo "Vault has been unsealed."
else
  echo "Vault is already unsealed."
fi

# Step 3: Log in to Vault with the Root Token
echo "-> Logging in to Vault with Root Token..."
export VAULT_TOKEN="$VAULT_ROOT_TOKEN"
# The login command is not needed if VAULT_TOKEN is exported, but it's good for verification.
vault login "$VAULT_TOKEN" > /dev/null

# Step 4: Enable the database secrets engine
echo "-> Enabling 'database' secrets engine..."
# Use || true to prevent script from exiting if it's already enabled
vault secrets enable -path=database/postgres database || true

# Step 5: Configure Vault's connection to PostgreSQL
echo "-> Configuring PostgreSQL connection for Vault..."
vault write database/postgres/config/root \
    plugin_name=postgresql-database-plugin \
    connection_url="postgresql://{{username}}:{{password}}@postgres:5432/postgres?sslmode=disable" \
    username="root" \
    password="root" \
    allowed_roles="vault-role"

# Step 6: Create a static role for the pre-existing 'vaultadmin'
# This role tells Vault how to manage the 'vaultadmin' account.
echo "-> Creating static role 'vault-role'..."
vault write database/postgres/static-roles/vault-role \
    db_name=root \
    credential_type=static_account \
    username="vaultadmin" \
    rotation_period="2d" \
    rotation_statements="ALTER USER \"{{name}}\" WITH PASSWORD '{{password}}';"

# Step 7: Test by reading the static credentials
echo "-> Reading credentials for the static role..."
# This will return the current password for 'vaultadmin'
vault read database/postgres/static-creds/vault-role

# Step 8: (Optional) Manually rotate the password now
echo "-> Forcing an immediate rotation of the static user's password..."
vault write -f database/postgres/rotate-role/vault-role

echo
echo "### Configuration Complete! ###"
echo "Vault is now managing the password for the 'vaultadmin' in PostgreSQL."
echo "Applications can request these credentials from Vault at the path."
