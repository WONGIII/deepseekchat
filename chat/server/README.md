# Account / chat backend

Small Node.js + Express service for the chat frontend.

- Serves the built frontend (`chat/dist`) and the PWA assets
- Registration/login with scrypt password hashing and HttpOnly session cookies
- Conversation JSON stored per user under `server/data/sessions/<userId>/`
- Per-user network settings (LLM/search/speech base URLs and API keys) encrypted with AES-256-GCM
- User registry, login tokens and settings under `server/data/` (ignored by git)
- `/api` endpoints are same-origin in production; the dev server proxies `/api` to 8098

## Run

```bat
cd chat/server
npm install
set HOST=0.0.0.0
set PORT=8098
node index.mjs
```

Build the frontend first (`pnpm build` from `chat/`).

## API

- `POST /api/auth/register` `{username,password}`
- `POST /api/auth/login` `{username,password}`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET /api/sessions` — summaries for the current user
- `GET /api/sessions/:id`
- `PUT /api/sessions/:id` — create/replace one session
- `PATCH /api/sessions/:id` — rename
- `DELETE /api/sessions/:id`
- `PUT /api/sessions` — replace all sessions (`{sessions:[...]}`), used by history import/clear
- `GET /api/settings/network` — current user's network settings, or `{settings:null}`
- `PUT /api/settings/network` — save `{settings:{llmBaseUrl,searchBaseUrl,speechBaseUrl,llmApiKey,searchApiKey,speechApiKey}}`

## Storage

`server/data/users.json`, `server/data/tokens.json`, per-user session JSON and per-user
`settings/<userId>.json` are local files. Do **not** commit `server/data`; it contains account
credentials, private conversations and API keys. Attachments themselves remain in the browser
IndexedDB; session JSON stores their metadata.

Network settings are encrypted with AES-256-GCM before being written. The 32-byte key is read
from the `SETTINGS_SECRET` environment variable (hashed with SHA-256), otherwise generated once
into `server/data/settings.key` with owner-only permissions. Back up or keep that key: without
it the saved settings cannot be decrypted. Authenticated users only receive their own settings.

## HTTPS / PWA

The manifest and service worker are served from the frontend build. Browsers require HTTPS
(or localhost) for PWA installation and service-worker registration; put this service behind
a trusted HTTPS reverse proxy when using it from a phone on the LAN.
