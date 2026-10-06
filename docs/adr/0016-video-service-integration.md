# ADR 0016: video-service joins the platform the same way as books-service

- Status: accepted
- Date: 2026-10-06
- Follows ADR 0015

## Context

After books-service, the owner asked for the same integration for `video-service`. It was in the same state: its own role checks (`ADMIN`, `MANAGER`), a realm that does not exist, its own Postgres and Keycloak, credentials in its files.

## Decision

Everything in ADR 0015 applies unchanged: clean architecture in `com.videos`, a platform login required, users from user-service (a video stores only `owner_id`), onboarding through the platform APIs, database credentials from Vault, the schema admin `videoadmin` and the runtime account `theuser` in `videodb`. What is specific to videos:

| Topic | Decision |
| --- | --- |
| Roles | Separate from the book catalog, by the owner's choice: `VIDEO_READER` (`videos:read`), `VIDEO_EDITOR` (+ `videos:write`), `VIDEO_MANAGER` (+ `videos:manage`); `PLATFORM_ADMIN` holds all three. A role for books does not open videos, and the reverse. |
| API | `/api/v1/videos`, with `POST /{id}/complete` for marking a video completed and `PUT /{id}/owner` for a transfer. The old `/api/videos` paths are gone. |
| Rules kept | A title is 1 to 30 characters and unique; a description at most 100 characters. These are the limits the service had; they were not changed. |
| Seed data | The service had none. A starter set of 20 sample videos was written for it and is loaded in dev, by the owner's choice. |
| Platform changes | `onboarding/video-service.json`, a Vault policy and token, the Compose block, Kubernetes manifests and a gateway route. The onboarding job, the Vault seeding of `secret/video-service` and the database job already covered it. The start, stop and status scripts now treat business services as a list. |

## Consequences

- Adding the next business service is the same short list; see [Integrating a new service](../integrating-a-new-service.md).
- Most of video-service's supporting code (paging, the user lookup adapter, error handling, security configuration, test helpers) is the same as in books-service, copied with the package renamed. If a third service needs it too, it should move into the shared starter instead of being copied again.
- `theuser` has access to both `booksdb` and `videodb`, as recorded in ADR 0015.
- The Compose stack with both business services uses a little over 5 GB of memory.
