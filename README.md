# DeepSeek Chat Local Stack

Private repository containing a customized DeepSeek chat frontend and a local speech stack:

```text
chat/          React + TypeScript chat frontend
cosyvoice3/    CosyVoice3 TTS + Whisper ASR adapter and vendored CosyVoice source
```

## What is included

- Custom network settings: separate LLM / search / speech base URLs and API keys
- Custom model selection, model fetching and manual model IDs
- Standalone DeepSeek search integration and local title fallback
- Web search, attachments, history import/export
- Local CosyVoice3 read-aloud with Chinese/Japanese/English/Korean/Russian
- Japanese katakana conversion and mixed Chinese/Japanese sentence routing
- Dynamic emotion mode, per-sentence pauses and smoother streaming UI
- Local Whisper ASR for voice input
- Current custom `stella` reference voice (`cosyvoice3/voices/stella.wav`)
- Windows one-click launcher

## What is NOT included

- CosyVoice3 model weights (about 5 GB)
- Whisper model weights (download on first ASR use)
- Python virtual environments
- Other/backup reference voice recordings
- API keys or local browser data

The repository contains download/setup scripts and instructions instead.

## Quick start

### 1. Chat frontend

Requires Node.js and pnpm.

```bat
cd chat
pnpm install
pnpm dev
```

Open `http://localhost:8090/`.

Configure Settings → Network with your LLM/search/speech endpoints and keys.

### 2. Speech service

Requires Python 3.10 and optionally CUDA.

```bat
cd cosyvoice3
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe torch==2.3.1+cu121 torchaudio==2.3.1+cu121 --index-url https://download.pytorch.org/whl/cu121
uv pip install --python .venv\Scripts\python.exe "setuptools<81"
uv pip install --python .venv\Scripts\python.exe -r requirements-tts.txt
uv pip install --python .venv\Scripts\python.exe openai-whisper==20231117 --no-deps --no-build-isolation
python download_model.py
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000
```

See [`cosyvoice3/README.md`](cosyvoice3/README.md) for model downloads, optional RL weights and voice configuration.

### 3. One-click Windows launch

After both environments have been installed once:

```bat
chat\start-all.bat
```

This starts the chat dev server and the speech adapter, waits for both, and opens the app. Close its console to stop everything.

## Default ports

- `8090` — chat dev server
- `8000` — CosyVoice3 TTS + Whisper ASR adapter

## Security

API keys and settings are stored in the browser's `localStorage`. Conversations and attachments are stored in IndexedDB. Nothing is uploaded to GitHub from those stores.

Do not commit `.env` files, API keys, additional private voice references or model weights.

## Upstream

The frontend is based on [`deepseek-ai/chat-demo`](https://github.com/deepseek-ai/chat-demo). CosyVoice source is vendored from [`FunAudioLLM/CosyVoice`](https://github.com/FunAudioLLM/CosyVoice); model weights remain on Hugging Face/ModelScope and are downloaded separately.
