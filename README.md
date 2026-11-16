# DeepSeek Chat Local Stack

Private repository containing a customized DeepSeek chat frontend and a local speech stack:

```text
chat/          React + TypeScript chat frontend
cosyvoice3/    CosyVoice3 TTS + Whisper ASR adapter and vendored CosyVoice source
m5-tts/        M5-TTS ONNX trial engine and adapter
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
- M5-TTS 50M ONNX trial engine (CUDA/CPU) with voice cloning
- Current custom `stella` reference voice (`cosyvoice3/voices/stella.wav`)
- Windows one-click launchers for both engines

## What is NOT included

- CosyVoice3 model weights (about 5 GB)
- M5-TTS ONNX model weights (about 1.5 GB)
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

### 3. M5-TTS trial (optional)

M5-TTS runs as a separate ONNX service and proxies through the same adapter:

```bat
cd m5-tts
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe -r requirements-onnx.txt
.venv\Scripts\python.exe m5_server.py --host 127.0.0.1 --port 8001 --device cuda
```

Then start the adapter in M5 mode:

```bat
cd ..\cosyvoice3
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 --tts-engine m5 --m5-url http://127.0.0.1:8001
```

See [`m5-tts/README.md`](m5-tts/README.md) for GPU DLL notes, voice mapping and limitations.

### 4. One-click Windows launch

After both environments have been installed once:

```bat
chat\start-all.bat
```

CosyVoice + Whisper is the default. For the M5 trial:

```bat
chat\start-all-m5.bat
```

Both launchers start the chat dev server and the speech adapter, wait for readiness, and open the app. Close the console to stop everything.

## Default ports

- `8090` — chat dev server
- `8000` — speech adapter (CosyVoice/Whisper by default, or M5 proxy)
- `8001` — M5-TTS ONNX service when the M5 trial launcher is used

## Security

API keys and settings are stored in the browser's `localStorage`. Conversations and attachments are stored in IndexedDB. Nothing is uploaded to GitHub from those stores.

Do not commit `.env` files, API keys, additional private voice references or model weights.

## Upstream

The frontend is based on [`deepseek-ai/chat-demo`](https://github.com/deepseek-ai/chat-demo). CosyVoice source is vendored from [`FunAudioLLM/CosyVoice`](https://github.com/FunAudioLLM/CosyVoice) and M5-TTS source from [`ASLP-lab/M5-TTS`](https://github.com/ASLP-lab/M5-TTS); model weights remain on Hugging Face/ModelScope and are downloaded separately.
