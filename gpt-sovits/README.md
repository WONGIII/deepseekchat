# GPT-SoVITS V5 Turbo local service

Trial deployment of [RVC-Boss/GPT-SoVITS](https://github.com/RVC-Boss/GPT-SoVITS)
V5 Turbo (`v5turbo`) for the chat-demo read-aloud feature.

- GPT: 77.6M parameters (`s1v3.ckpt`)
- SoVITS V5 Turbo: 383.1M parameters (`s2Gv5turbo.pth`)
- vocoder: 14.4M parameters
- 4-step sampling (Turbo), **native 48 kHz mono** output; the 8000 adapter forwards the native sample rate via `X-TTS-Sample-Rate` and the browser plays it without downsampling
- zero-shot voice cloning with local reference clips (`stella` / `tide`, prompt text Chinese)

## Local layout

```text
repo/                 GPT-SoVITS source (patched for Windows/local deps)
gsv_server.py         FastAPI /tts wrapper
start.bat             start standalone service on 8002
.venv/                local Python environment (not for git)
```

`gsv_server.py` starts on port **8002** so it can coexist with the M5 service on 8001.

## Model files (not included in this directory's git history)

Download into `repo/GPT_SoVITS/pretrained_models`:

- `s1v3.ckpt`
- `gsv-v5-pretrained/s2Gv5turbo.pth`
- `gsv-v5-pretrained/vocoder.pth`
- `chinese-roberta-wwm-ext-large/` (config, tokenizer, `pytorch_model.bin`)
- `chinese-hubert-base/` (config, preprocessor, `pytorch_model.bin`)

From this deployment, the files were downloaded with:

```python
from huggingface_hub import snapshot_download
snapshot_download(
    "lj1995/GPT-SoVITS",
    allow_patterns=[
        "s1v3.ckpt",
        "gsv-v5-pretrained/s2Gv5turbo.pth",
        "gsv-v5-pretrained/vocoder.pth",
        "chinese-roberta-wwm-ext-large/*",
        "chinese-hubert-base/*",
    ],
    local_dir="repo/GPT_SoVITS/pretrained_models",
)
```

## Setup (Windows, Python 3.10)

```bat
cd gpt-sovits
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe --no-deps torch==2.3.1+cu121 torchaudio==2.3.1+cu121 --index-url https://download.pytorch.org/whl/cu121
uv pip install --python .venv\Scripts\python.exe numpy==1.26.4 scipy librosa==0.10.2 soundfile pyyaml tqdm psutil chardet sentencepiece transformers==4.51.3 peft x_transformers rotary_embedding_torch pypinyin cn2an jieba wordsegment g2p_en "nltk==3.8.1" ffmpeg-python fastapi uvicorn requests pyopenjtalk-prebuilt==0.3.0 resampy
uv pip install --python .venv\Scripts\python.exe --no-deps pytorch-lightning==2.4.0 lightning-utilities torchmetrics==1.5.0
uv pip install --python .venv\Scripts\python.exe matplotlib==3.7.5 "setuptools<81"
```

Dependency notes:

- `pyopenjtalk-prebuilt` has a Windows cp310 wheel; it downloads the Open JTalk dictionary on first Japanese synthesis.
- `jieba_fast` has no Windows cp310 wheel. The local `repo/GPT_SoVITS/text/*.py` files are patched to use `jieba`; `text/tone_sandhi.py` is patched too.
- The fast_langdetect-based `LangSegmenter` is replaced with a small regex Latin/CJK splitter; `gsv_server.py` always supplies a target language, so this is sufficient.
- `G2PW` is disabled (`is_g2pw = False` in `text/chinese2.py`), using pypinyin instead; this avoids a 589 MB G2PW download. Chinese polyphone accuracy may be slightly lower.
- `resampy` is required for reference audio loading.

## Start

```bat
start.bat
```

or:

```bat
.venv\Scripts\python.exe gsv_server.py --host 127.0.0.1 --port 8002 --device cuda
```

Health: `http://127.0.0.1:8002/health`

The service warms the prompt cache with a short utterance at startup.

## Use through the website

Start the adapter on 8000 in GPT-SoVITS mode:

```bat
cd ..\cosyvoice3
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 --tts-engine gsv --gsv-url http://127.0.0.1:8002
```

Or run the full stack:

```bat
..\chat\start-all-gsv.bat
```

Website → Settings → Network → speech base URL: `http://127.0.0.1:8000`.
ASR remains Whisper; TTS is forwarded to GPT-SoVITS on 8002.

## Behaviour / limits

- V5 Turbo uses non-streaming synthesis in this local pipeline; the server splits text per sentence and returns one chunk per sentence.
- First request after startup is slow (about 8 s for the first Chinese request); warmed requests are about 0.8–2 s to first audio on the RTX 4070 Laptop.
- `emotion` / dynamic tone settings are ignored; GPT-SoVITS is voice cloning + text, not instruct-controlled in this setup.
- `tide` uses `cosyvoice3/voices/tide.wav` with its Chinese prompt text; if the file is absent it falls back to the stella reference.
- Mixed Chinese/Japanese sentences are routed per sentence, but Whisper transcribing mixed audio can label the Japanese part as Chinese; verify synthesis via `gsv.log`.
- The adapter still provides Whisper ASR on port 8000 while GPT-SoVITS serves TTS on 8002.
