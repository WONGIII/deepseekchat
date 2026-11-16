# M5-TTS ONNX local service

Trial deployment of [ASLP-lab/M5-TTS](https://github.com/ASLP-lab/M5-TTS) for the chat-demo
read-aloud feature.

- 50M-parameter multilingual TTS LM (codec excluded)
- 24 kHz mono output
- zero-shot voice cloning
- Chinese / English / Japanese / Korean
- ONNX Runtime CUDA or CPU

The service exposes the same `/tts` PCM protocol as the CosyVoice adapter, so the website
can switch engines without changing its speech request code.

## What is here

```text
repo/              upstream M5-TTS source
m5_server.py       local FastAPI wrapper + voice mapping + language routing
start.bat          start the standalone M5 service on 8001
.venv/             local Python environment (not for git)
```

Model weights are **not** committed. `m5_server.py` downloads
`ASLP-lab/M5-TTS-50M-ONNX` (~1.5 GB) from Hugging Face on first start into the Hugging Face cache.

## Setup (Windows, Python 3.10)

```bat
cd m5-tts
uv venv .venv --python 3.10
uv pip install --python .venv\Scripts\python.exe numpy==1.26.4 soundfile scipy huggingface_hub tokenizers cn2an fastapi uvicorn requests
uv pip install --python .venv\Scripts\python.exe onnxruntime-gpu==1.23.2 nvidia-cublas-cu12 nvidia-cuda-runtime-cu12 nvidia-cufft-cu12 "nvidia-cudnn-cu12==9.10.2.21"
```

`nvidia-cudnn-cu12==9.28.x` failed with `cudnn_engines_tensor_ir64_9.dll` on ONNX Runtime
1.23.2; 9.10.2.21 works. The server adds the NVIDIA DLL directories automatically.

For CPU-only inference, install `onnxruntime` instead of `onnxruntime-gpu` and start with
`--device cpu`. The service falls back to CPU if CUDA initialization fails.

## Start

Standalone service:

```bat
start.bat
```

or:

```bat
.venv\Scripts\python.exe m5_server.py --host 127.0.0.1 --port 8001 --device cuda
```

Health: `http://127.0.0.1:8001/health`

## Use through the website

Start the CosyVoice ASR adapter in M5 proxy mode (port 8000):

```bat
cd ..\cosyvoice3
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 --tts-engine m5 --m5-url http://127.0.0.1:8001
```

Or run the full stack:

```bat
..\chat\start-all-m5.bat
```

Website → Settings → Network → speech base URL: `http://127.0.0.1:8000`.
ASR remains Whisper; TTS is forwarded to M5 at 8001.

## Voice mapping

- `stella` → `..\cosyvoice3\voices\stella.wav` with the existing stella prompt transcript
- `tide` → M5's official `assets/aslp_default_voice.wav`; this is not a dedicated male voice

M5 is used in voice-cloning mode, so the prompt transcript matters. The current stella prompt
is the same one used by the CosyVoice adapter.

## Behaviour

- Text is split per sentence; language is detected per sentence.
- Decorative glyphs (`♥`, `♡`, emoji, `～`) are removed/replaced for M5 because they cause
  mispronunciation; `……` is kept.
- M5 ONNX does not expose emotion instructions. The website's `emotion` setting is ignored
  while TTS is routed through M5.
- The server caches the encoded reference voice across sentences.
- Sentence pauses are inserted between sentences.

## Measured on this machine

RTX 4070 Laptop, ONNX CUDA, 4 threads:

- first PCM chunk: about 0.26–0.87 s
- Chinese / Japanese RTF: about 0.7–0.9
- CPU fallback: first chunk about 0.55–2 s, RTF about 1.2–1.5 (not real-time for long text)
