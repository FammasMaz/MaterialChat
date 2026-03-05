# OpenClaw -> FCM Push (Direct Server)

This is the clean path with no extra relay service:

1. Android app registers FCM token directly to OpenClaw HTTP routes.
2. OpenClaw stores tokens per agent.
3. OpenClaw plugin/hook sends FCM on final agent responses.
4. App receives push and shows notification with inline reply.

## App behavior in this repo

- App sends token to the OpenClaw server configured in OpenClaw settings.
- Endpoints used:
  - `POST /materialchat/push/register-token`
  - `POST /materialchat/push/unregister-token`
- Auth: `Authorization: Bearer <gateway-token>`.
- Registration is automatic when:
  - Notifications are enabled
  - OpenClaw is enabled and configured
  - Gateway token exists
- On setup save, the app attempts to auto-create the selected agent via Gateway RPC (`agents.list` + `agents.create`).

## Required app setup

1. Add `google-services.json` to `app/`.
2. Build and install app.

The Android module applies Google Services only if `app/google-services.json` exists,
so local builds still work before Firebase is configured.

## Required server setup (OpenClaw host)

1. Install/enable a plugin that exposes the two `materialchat/push/*` routes.
2. Plugin stores `{ token, agentId, platform, appVersion, updatedAt }`.
3. Plugin listens for outbound final assistant messages and sends FCM using Firebase Admin SDK.
4. Remove invalid tokens when FCM returns permanent errors.

## Dedicated agent recommendation

Create a dedicated agent for app traffic (for isolation + predictable routing), e.g. `materialchat`.
Use app default agent = `materialchat` and keep a stable agent-scoped session key strategy.

## Security

- Keep push routes behind Gateway auth (`auth: gateway` in plugin routes).
- Validate payload schema and body size.
- Restrict accepted `agentId` values on server side.

## FCM payload recommendation

- Use data payload keys:
  - `type=openclaw_chat`
  - `agentId=<agent id>`
  - `sessionKey=<session key>`
  - `contentPreview=<short text>`
- Use high Android priority only for user-visible messages.

## Battery notes

- FCM is the primary path for killed/background app delivery.
- WorkManager polling fallback should only run when push registration is unhealthy.
