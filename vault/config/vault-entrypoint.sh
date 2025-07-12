#!/bin/sh
# Exit immediately if a command exits with a non-zero status.
set -e

# The VAULT_DEV_ROOT_TOKEN_ID and VAULT_DEV_LISTEN_ADDRESS env vars from compose.yml
# will be used by the server.
# Start Vault server in the background and pass the config file.
echo "Starting Vault server in dev mode..."
vault server -dev &
# Store the Vault server's process ID
VAULT_PID=$!

# Wait for Vault to be ready by polling its status
echo "Waiting for Vault to become available..."
until vault status > /dev/null 2>&1; do
  echo "..."
  sleep 1
done
echo "Vault is up and running!"

# The VAULT_ADDR and VAULT_DEV_ROOT_TOKEN_ID env vars are already set in compose.yml,
# so the vault CLI is ready to use.
echo "Initializing Vault with secrets..."
vault kv put secret/db/root username=root password=root profile=dev
echo "Secret 'secret/db/root' created."
vault kv put secret/db username=theuser password=theuser profile=dev
echo "Secret 'secret/db' created."
vault kv put secret/keys api-key=apikey111,apikey222 key-secret=Secret123@@321terceSSecret123@@321terceS@321terceS profile=dev
echo "Secret 'secret/keys' created."

vault kv get secret/db/root
vault kv get secret/db
vault kv get secret/keys

echo "Initialization complete."

# Bring the Vault server process to the foreground
wait $VAULT_PID