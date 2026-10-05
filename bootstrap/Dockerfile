# One-shot bootstrap image: the Vault CLI plus curl and jq for the Keycloak Admin API.
ARG VAULT_VERSION
FROM hashicorp/vault:${VAULT_VERSION}
# The Vault image defaults to an unprivileged user; installing packages needs root.
USER root
RUN apk add --no-cache curl jq
COPY scripts/vault-bootstrap.sh scripts/keycloak-bootstrap.sh /bootstrap/
RUN chmod +x /bootstrap/*.sh
USER vault
ENTRYPOINT ["/bin/sh", "-c", "/bootstrap/vault-bootstrap.sh && /bootstrap/keycloak-bootstrap.sh"]
