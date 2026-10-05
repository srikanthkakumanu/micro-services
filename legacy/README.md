# Parked legacy assets

Copies of the pre-clean-slate platform files that still reference `books-service`, `video-service` and `reviews-service`. They are kept only so those out-of-scope services have a reference until they are onboarded onto the new identity platform.

- `compose.yml`, `docker-bake.hcl`: the old shared stack (all eight services, realm `company-platform`).
- `k8s/`: the old manifests for the three services.

They are **unmaintained and not expected to work** against the new `platform` realm. Paths inside them are relative to the repository root, not to this folder. `../postgres-init/` is left in place for the same reason.

Onboarding those services is done through the APIs; see `docs/integrating-a-new-service.md`.
