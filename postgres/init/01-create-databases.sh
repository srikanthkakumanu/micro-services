#!/bin/sh
# Runs once, on a fresh Postgres volume. Creates one database and one owning user per context.
# Passwords come from the environment (generated into .env by scripts/init-env.sh and then
# written to Vault by scripts/vault-bootstrap.sh). No service reads another's database.
set -eu

create_owned_database() {
  database=$1
  owner=$2
  password=$3
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v owner="$owner" -v password="$password" -v database="$database" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'password';
CREATE DATABASE :"database" OWNER :"owner";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL
}

create_owned_database keycloak keycloak "${KEYCLOAK_DB_PASSWORD:?}"
create_owned_database user_db user_service "${USER_DB_PASSWORD:?}"
create_owned_database auth_db auth_service "${AUTH_DB_PASSWORD:?}"
