# DeepSeek Chat Local Stack

Private repository containing a customized DeepSeek chat application with accounts,
server-side conversation storage, a PWA shell, and local speech services.

```text
chat/          React + TypeScript frontend
chat/server/   Node account/chat backend (JSON storage, HttpOnly cookie auth)
android-app/   Native Android client (Kotlin + Jetpack Compose)
cosyvoice3/    CosyVoice3 TTS + Whisper ASR adapter and vendored CosyVoice source
m5-tts/        M5-TTS 50M ONNX trial engine
gpt-sovits/    GPT-SoVITS V5 Turbo trial engine (native 48 kHz)
```

## Features

- Per-user registration/login; conversations are stored on the server, isolated by account
- PWA manifest, icons and service worker for mobile install
- Per-user LLM / search / speech base URLs and API keys, encrypted at rest on the server
- Custom model selection, model fetching and manual model IDs
- Standalone DeepSeek search integration and local title fallback
- CosyVoice3 read-aloud with Chinese/Japanese/English/Korean/Russian
- GPT-SoVITS V5 Turbo read-aloud with native 48 kHz output
- M5-TTS 50M ONNX trial engine
- Local Whisper ASR for voice input
- Streaming text, thinking-chain animation, HTML preview and responsive mobile UI
- Native Android app with shared-element transitions, local-first storage, optional cloud login,
  local PDF/Office text extraction and local TTS/ASR playback
- All services bind to `0.0.0.0` by default

## Not included

- CosyVoice3 model weights (~5 GB)
- GPT-SoVITS V5 Turbo model weights (~1.7 GB)
- M5-TTS ONNX model weights (~1.5 GB)
- Whisper model weights (downloaded on first ASR use)
- Python/Node virtual environments
- Server user data (`chat/server/data`)
- Private API keys or extra voice recordings

Model download/setup instructions live in the service READMEs.

## Quick start

### 1. Frontend + account backend

Requires Node.js and pnpm.

```bat
cd chat
pnpm install
cd server
npm install
cd ..
pnpm build
```

Start the backend (serves the built frontend and `/api` on port 8098):

```bat
cd server
set HOST=0.0.0.0
set PORT=8098
node index.mjs
```

Open `http://localhost:8098/` and register an account.

For frontend development instead, run `start-chat.bat` (port 8090) while the backend is
running; `/api` is proxied to 8098.

### 2. CosyVoice + Whisper speech service

```bat
cd cosyvoice3
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe torch==2.3.1+cu121 torchaudio==2.3.1+cu121 --index-url https://download.pytorch.org/whl/cu121
uv pip install --python .venv\Scripts\python.exe "setuptools<81"
uv pip install --python .venv\Scripts\python.exe -r requirements-tts.txt
uv pip install --python .venv\Scripts\python.exe openai-whisper==20231117 --no-deps --no-build-isolation
python download_model.py
```

See [`cosyvoice3/README.md`](cosyvoice3/README.md) for RL weights and voice setup.

### 3. Optional engines

- GPT-SoVITS V5 Turbo: [`gpt-sovits/README.md`](gpt-sovits/README.md)
- M5-TTS: [`m5-tts/README.md`](m5-tts/README.md)

Both expose the same adapter protocol and keep Whisper ASR on port 8000.

### 4. One-click Windows scripts

Full stacks build the frontend, start the account backend, speech adapter and selected
engine, then open `http://localhost:8098/`:

```bat
chat\start-all.bat        :: CosyVoice + Whisper
chat\start-all-gsv.bat    :: GPT-SoVITS V5 Turbo + Whisper
chat\start-all-m5.bat     :: M5-TTS + Whisper
```

Individual services:

```bat
chat\start-backend.bat    :: build + account/chat backend 8098
chat\start-chat.bat       :: frontend dev server 8090
chat\start-tts.bat        :: CosyVoice + Whisper 8000
chat\start-gsv.bat        :: GPT-SoVITS 8002
chat\start-m5.bat         :: M5-TTS 8001
chat\stop-all.bat         :: stop all local services
```

## Ports

- `8098` — account/chat backend + production frontend/PWA
- `8090` — frontend dev server
- `8000` — speech adapter (TTS proxy + Whisper ASR)
- `8001` — M5-TTS (optional)
- `8002` — GPT-SoVITS (optional)

### 5. Native Android app

The `android-app/` project is a native Jetpack Compose client. Open it in Android Studio or
build from the command line:

```bat
cd android-app
gradlew assembleRelease
```

The default mode is local-first: conversations and settings stay on the device. A server URL
can be configured later for multi-user cloud sessions. Speech and model endpoints are set in
the app's own settings page, independent of the web build.

## PWA / phone

The backend serves `manifest.webmanifest`, `sw.js` and icons. The service worker and
installation prompt require a **secure context**: use HTTPS (a reverse proxy with a trusted
certificate) or `localhost`. On plain `http://<LAN-IP>:8098` the app still opens on a phone,
but Android/iOS will not install it as a PWA or enable offline mode.

## Security

- Passwords are hashed with scrypt; login uses an HttpOnly, SameSite=Lax cookie.
- Conversations are stored under `chat/server/data/sessions/<userId>/`; this directory is ignored by git.
- Network settings (base URLs and API keys) are stored per account on the server, encrypted with AES-256-GCM; the key comes from `SETTINGS_SECRET` or is generated into `chat/server/data/settings.key`.
- UI preferences (locale, theme, voice, model selection) remain in localStorage per browser profile.
- The local TTS/ASR services have no authentication; expose them only on trusted networks.

## Upstream

The frontend is based on [`deepseek-ai/chat-demo`](https://github.com/deepseek-ai/chat-demo).
CosyVoice source is vendored from [`FunAudioLLM/CosyVoice`](https://github.com/FunAudioLLM/CosyVoice),
M5-TTS from [`ASLP-lab/M5-TTS`](https://github.com/ASLP-lab/M5-TTS), and GPT-SoVITS from
[`RVC-Boss/GPT-SoVITS`](https://github.com/RVC-Boss/GPT-SoVITS); model weights are downloaded separately.
