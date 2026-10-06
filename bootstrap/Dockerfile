# Image of the one-shot jobs that set the platform up: the Vault CLI, curl and jq for the HTTP
# APIs, and psql for creating databases. Each job runs one of the scripts below.
ARG VAULT_VERSION
FROM hashicorp/vault:${VAULT_VERSION}
# The Vault image defaults to an unprivileged user; installing packages needs root.
USER root
RUN apk add --no-cache curl jq postgresql-client
COPY scripts/vault-bootstrap.sh scripts/fetch-runtime-secrets.sh scripts/db-init.sh \
     scripts/keycloak-bootstrap.sh scripts/onboard-services.sh /bootstrap/
COPY onboarding/ /bootstrap/onboarding/
RUN chmod +x /bootstrap/*.sh
USER vault
ENTRYPOINT ["/bin/sh"]
