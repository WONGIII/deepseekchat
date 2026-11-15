# CosyVoice3 TTS + Whisper ASR

Local speech service for the `chat` app in this repository.

- `POST /tts` / `/v1/tools/tts` — 24 kHz mono 16-bit PCM, streamed sentence by sentence
- `POST /asr` / `/v1/tools/asr` — multipart upload, returns `{ "text": "..." }`
- CosyVoice3 supports Chinese, English, Japanese, Korean, Russian and more
- Mixed Chinese/Japanese text is split per sentence and spoken in the matching language
- Japanese is converted to spaced katakana with Sudachi before synthesis
- `dynamic` emotion mode selects a different sub-tone per sentence
- Per-sentence silence is inserted to avoid hard cuts

## Model files are not included

This repository contains source code only. It does **not** contain CosyVoice model weights.

Download them with:

```bat
cd cosyvoice3
python download_model.py
```

The script downloads the inference files for `FunAudioLLM/Fun-CosyVoice3-0.5B-2512` into:

```text
cosyvoice3/repo/pretrained_models/Fun-CosyVoice3-0.5B
```

Optional RL weights: `download_model.py` also downloads `llm.rl.pt`. To use it, replace `llm.pt` with `llm.rl.pt` after downloading:

```bat
copy /Y repo\pretrained_models\Fun-CosyVoice3-0.5B\llm.rl.pt repo\pretrained_models\Fun-CosyVoice3-0.5B\llm.pt
```

Whisper is loaded with `openai-whisper`; its `small` weights are downloaded automatically the first time ASR starts.

## Setup (Windows, Python 3.10)

```bat
cd cosyvoice3
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe torch==2.3.1+cu121 torchaudio==2.3.1+cu121 --index-url https://download.pytorch.org/whl/cu121
uv pip install --python .venv\Scripts\python.exe "setuptools<81"
uv pip install --python .venv\Scripts\python.exe -r requirements-tts.txt
uv pip install --python .venv\Scripts\python.exe openai-whisper==20231117 --no-deps --no-build-isolation
python download_model.py
```

For CPU-only inference, install a CPU build of PyTorch instead of `+cu121`.

Start the service:

```bat
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000
```

Or use `start.bat`.

## Voice reference

`stella` uses `voices/stella.wav` when present. If it is absent, the official `repo/asset/zero_shot_prompt.wav` and official transcript are used. See `voices/README.md`.

`tide` uses the official `repo/asset/cross_lingual_prompt.wav`.

## Website configuration

In `chat` → Settings → Network:

- speech base URL: `http://127.0.0.1:8000`
- speech API key: blank (falls back to the LLM key; the local adapter ignores auth)

## One-click launcher

The Windows launcher lives in the chat directory and starts chat + this speech service:

```bat
chat\start-all.bat
```

It writes logs to `chat\logs\`.
