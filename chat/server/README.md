# Account / chat backend

Small Node.js + Express service for the chat frontend.

- Serves the built frontend (`chat/dist`) and the PWA assets
- Registration/login with scrypt password hashing and HttpOnly session cookies
- Conversation JSON stored per user under `server/data/sessions/<userId>/`
- User registry and login tokens under `server/data/` (ignored by git)
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

## Storage

`server/data/users.json`, `server/data/tokens.json` and per-user session JSON are local files.
Do **not** commit `server/data`; it contains account credentials and private conversations.
Attachments themselves remain in the browser IndexedDB; session JSON stores their metadata.

## HTTPS / PWA

The manifest and service worker are served from the frontend build. Browsers require HTTPS
(or localhost) for PWA installation and service-worker registration; put this service behind
a trusted HTTPS reverse proxy when using it from a phone on the LAN.
