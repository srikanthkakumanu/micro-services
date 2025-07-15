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
# Call the external script to initialize secrets.
# We assume the script is in the same directory.
echo "Initializing Vault with secrets..."
chmod +x /usr/local/bin/init-vault-secrets.sh
/usr/local/bin/init-vault-secrets.sh
echo "Vault Secrets Initialization complete."

# Bring the Vault server process to the foreground
wait $VAULT_PID