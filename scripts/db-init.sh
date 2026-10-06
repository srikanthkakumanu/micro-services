#!/bin/sh
# Creates the databases and their users. Every user name and password is read from Vault; none
# comes from the environment. Safe to run again: what exists is kept, and each password is set
# to the one Vault holds, so changing a password in Vault and re-running this applies it.
#
#   keycloak   owned by its own user
#   user_db    owned by user-service's user
#   auth_db    owned by auth-service's user
#   booksdb    owned by its admin (schema and migrations); a runtime user reads and writes rows
#   videodb    the same, with its own admin and the same runtime user
#
# No service is given access to another service's database.
#
# Required: VAULT_ADDR, VAULT_TOKEN (a read-only token), PGHOST.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${PGHOST:?}"

MOUNT=secret

secret() {
  vault kv get -mount="$MOUNT" -field="$2" "$1"
}

PGUSER=$(secret postgres username)
PGPASSWORD=$(secret postgres password)
export PGUSER PGPASSWORD

# Shell functions share their variables, so each function below uses names of its own.
run_sql() {
  sql_target=$1
  shift
  psql --quiet --no-psqlrc -v ON_ERROR_STOP=1 --dbname "$sql_target" "$@"
}

ensure_role() {
  run_sql postgres -v name="$1" -v password="$2" <<'SQL' > /dev/null
SELECT format('CREATE ROLE %I LOGIN', :'name')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'name') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'name', :'password') \gexec
SQL
}

ensure_database() {
  run_sql postgres -v database="$1" -v owner="$2" <<'SQL' > /dev/null
SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'owner')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database') \gexec
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
SQL
}

# One owner that both migrates and runs.
owned_database() {
  ensure_role "$2" "$3"
  ensure_database "$1" "$2"
  echo "Database $1: owner $2."
}

# An admin that owns the schema, and a runtime user limited to rows in the admin's tables.
admin_and_runtime_database() {
  shared_database=$1
  shared_admin=$2
  shared_runtime=$4
  ensure_role "$shared_admin" "$3"
  ensure_role "$shared_runtime" "$5"
  ensure_database "$shared_database" "$shared_admin"
  run_sql "$shared_database" -v database="$shared_database" -v admin="$shared_admin" -v runtime="$shared_runtime" <<'SQL' > /dev/null
GRANT CONNECT ON DATABASE :"database" TO :"runtime";
GRANT USAGE ON SCHEMA public TO :"runtime";
REVOKE CREATE ON SCHEMA public FROM :"runtime";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime";
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime";
SQL
  echo "Database $shared_database: admin $shared_admin, runtime user $shared_runtime."
}

owned_database "$(secret keycloak-db database)" "$(secret keycloak-db username)" "$(secret keycloak-db password)"
owned_database user_db "$(secret user-service spring.datasource.username)" "$(secret user-service spring.datasource.password)"
owned_database auth_db "$(secret auth-service spring.datasource.username)" "$(secret auth-service spring.datasource.password)"
admin_and_runtime_database booksdb \
  "$(secret books-service spring.flyway.user)" "$(secret books-service spring.flyway.password)" \
  "$(secret books-service spring.datasource.username)" "$(secret books-service spring.datasource.password)"
admin_and_runtime_database videodb \
  "$(secret video-service spring.flyway.user)" "$(secret video-service spring.flyway.password)" \
  "$(secret video-service spring.datasource.username)" "$(secret video-service spring.datasource.password)"

echo "Databases ready."
