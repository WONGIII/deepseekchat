"""GPT-SoVITS V5 Turbo HTTP service for the chat-demo frontend.

Uses the local GPT-SoVITS inference pipeline in voice-cloning mode and exposes
the same 24 kHz mono PCM /tts protocol as the CosyVoice and M5 adapters.
"""

import argparse
import asyncio
import logging
import os
import re
import sys
import threading

import numpy as np
import uvicorn

# Windows logs are redirected with a GBK locale; model text often contains emoji.
# Reconfigure the streams so a debug print can never kill the streaming response.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(ROOT, "repo")
COSY = os.path.abspath(os.path.join(ROOT, "..", "cosyvoice3"))
sys.path.insert(0, os.path.join(REPO, "GPT_SoVITS"))
os.chdir(REPO)

OUTPUT_SAMPLE_RATE = 48000
STELLA_WAV = os.path.join(COSY, "voices", "stella.wav")
STELLA_PROMPT = (
    "是想聊点让人心跳加快的，还是想让这声你好变成喘息的？"
    "嗯，你说嘛，人家都听你的，爸爸。"
)
TIDE_WAV = os.path.join(COSY, "voices", "tide.wav")
TIDE_PROMPT = (
    "香港今日11月17日大致多雲，日間部分時間有陽光，天氣偏暖。"
    "不過要留意，今晚氣溫會開始下降，隨後一兩日顯著轉涼，記得準備定外套。"
)
if not os.path.exists(TIDE_WAV):
    logging.getLogger("gpt-sovits").warning(
        "tide.wav not found; Tide falls back to the Stella reference"
    )
    TIDE_WAV, TIDE_PROMPT = STELLA_WAV, STELLA_PROMPT
VOICES = {"stella": (STELLA_WAV, STELLA_PROMPT), "tide": (TIDE_WAV, TIDE_PROMPT)}

HIRAGANA_RE = re.compile(r"[\u3041-\u3096\u309D-\u309F]")
KATAKANA_RE = re.compile(r"[\u30A1-\u30FA\u30FD-\u30FF]")
HANGUL_RE = re.compile(r"[\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]")
CHINESE_RE = re.compile(r"[\u3400-\u4DBF\u4E00-\u9FFF]")
SPEAKABLE_RE = re.compile(
    r"[0-9A-Za-z\u3041-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uAC00-\uD7AF]"
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("gpt-sovits")

tts_pipeline = None
infer_lock = threading.Lock()


def clean_markdown(text):
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"\[citation:\d+\]", "", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"https?://\S+", "", text)
    text = re.sub(r"^\s*\|.*\|\s*$", "", text, flags=re.M)
    text = re.sub(r"^\s*[#>*_-]+\s*", "", text, flags=re.M)
    text = re.sub(r"~~([^~]*?)~~", r"\1", text)
    text = re.sub(r"[*_#>]+", "", text)
    text = re.sub(r"\$[^$]*\$", "", text)
    text = text.replace("\n", "。")
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def split_sentences(text, max_len=180):
    parts = re.split(r"(?<=[。！？!?；;])|\n+", text)
    chunks = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        while len(part) > max_len:
            cut = max(
                part.rfind("，", 0, max_len),
                part.rfind(",", 0, max_len),
                part.rfind(" ", 0, max_len),
            )
            if cut <= 0:
                cut = max_len - 1
            chunks.append(part[: cut + 1].strip())
            part = part[cut + 1 :].strip()
        if part:
            chunks.append(part)
    return chunks


def detect_language(text):
    if HIRAGANA_RE.search(text) or KATAKANA_RE.search(text):
        return "ja"
    if HANGUL_RE.search(text):
        return "ko"
    if CHINESE_RE.search(text):
        return "zh"
    return "en"


def sentence_pause_ms(text, language=None, next_language=None):
    stripped = text.rstrip(" \t\r\n\"'」』）】")
    ended_period = stripped.endswith("。")
    tail = stripped.rstrip("。")
    if tail.endswith(("……", "…", "...")):
        pause = 480
    elif tail.endswith(("？", "?")):
        pause = 360
    elif tail.endswith(("！", "!")):
        pause = 320
    elif ended_period:
        pause = 280
    elif tail.endswith(("，", ",", "、", "：", ":")):
        pause = 140
    else:
        pause = 80
    if language and next_language and language != next_language:
        pause += 160
    return pause


def silence_pcm(ms):
    return b"\x00\x00" * int(OUTPUT_SAMPLE_RATE * ms / 1000)


def to_pcm16(audio, sample_rate):
    # GPT-SoVITS V5 Turbo already produces 48 kHz mono; keep the native signal.
    audio = np.asarray(audio)
    if audio.ndim > 1:
        audio = audio.mean(axis=0)
    audio = np.clip(audio, -32768, 32767).astype(np.int16)
    return audio.tobytes()


def generate_pcm(text, voice_id, filter_markdown, language_override, cancel_event=None):
    if filter_markdown:
        text = clean_markdown(text)
    voice_path, prompt_text = VOICES.get(voice_id, VOICES["stella"])
    sentences = [s for s in split_sentences(text) if SPEAKABLE_RE.search(s)]

    prepared = []
    for raw in sentences:
        lang = language_override or detect_language(raw)
        if lang not in ("zh", "ja", "en"):
            lang = "zh" if CHINESE_RE.search(raw) else "en"
        prepared.append((raw, lang))

    for index, (sentence, lang) in enumerate(prepared):
        if cancel_event is not None and cancel_event.is_set():
            log.info("gsv synthesis cancelled by client disconnect")
            break
        log.info("gsv synthesize [%s]: %s", lang, sentence[:60])
        with infer_lock:
            for sr, audio in tts_pipeline.run(
                {
                    "text": sentence,
                    "text_lang": lang,
                    "ref_audio_path": voice_path,
                    "prompt_text": prompt_text,
                    "prompt_lang": "zh",
                    "text_split_method": "cut1",
                    "batch_size": 1,
                    "speed_factor": 1.0,
                    "seed": 1234 + index,
                    "sample_steps": 4,
                    "streaming_mode": False,
                    "return_fragment": False,
                    "fragment_interval": 0.0,
                    "parallel_infer": True,
                    "use_cuda_graph": False,
                    "use_flash_attention": False,
                }
            ):
                if cancel_event is not None and cancel_event.is_set():
                    break
                yield to_pcm16(audio, sr)
        if index + 1 < len(prepared):
            next_lang = prepared[index + 1][1]
            yield silence_pcm(sentence_pause_ms(sentence, lang, next_lang))


app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["X-TTS-Sample-Rate"],
)


@app.get("/health")
def health():
    return {
        "status": "ok" if tts_pipeline is not None else "loading",
        "model": "GPT-SoVITS-v5turbo",
        "engine": "gpt-sovits",
        "sample_rate": OUTPUT_SAMPLE_RATE,
        "voices": list(VOICES),
        "languages": ["zh", "ja", "en"],
    }


@app.post("/tts")
@app.post("/v1/tools/tts")
async def tts(request: Request):
    if tts_pipeline is None:
        return JSONResponse({"error": {"message": "model is not ready"}}, status_code=503)
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"error": {"message": "invalid json"}}, status_code=400)

    text = str(body.get("text") or "").strip()
    if not text:
        return JSONResponse({"error": {"message": "text is empty"}}, status_code=400)
    voice = str(body.get("voice_id") or "stella")
    if voice not in VOICES:
        voice = "stella"
    filter_markdown = bool(body.get("filter_markdown", True))
    requested = str(body.get("language") or "auto").strip().lower()
    language_override = {
        "zh": "zh",
        "chinese": "zh",
        "ja": "ja",
        "japanese": "ja",
        "en": "en",
        "english": "en",
    }.get(requested, None)

    cancel_event = threading.Event()

    async def watch_disconnect():
        try:
            while not cancel_event.is_set():
                if await request.is_disconnected():
                    log.info("gsv client disconnected, stopping synthesis")
                    cancel_event.set()
                    return
                await asyncio.sleep(0.1)
        except asyncio.CancelledError:
            return

    watcher = asyncio.create_task(watch_disconnect())

    def stream_with_cancel():
        try:
            yield from generate_pcm(
                text, voice, filter_markdown, language_override, cancel_event
            )
        finally:
            cancel_event.set()
            watcher.cancel()

    return StreamingResponse(
        stream_with_cancel(),
        media_type="audio/pcm",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "X-TTS-Sample-Rate": str(OUTPUT_SAMPLE_RATE),
        },
    )


def main():
    global tts_pipeline
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--device", choices=("cuda", "cpu"), default="cuda")
    parser.add_argument("--fp32", action="store_true", help="disable fp16")
    args = parser.parse_args()

    base = os.path.join(REPO, "GPT_SoVITS", "pretrained_models")
    configs = {
        "custom": {
            "device": args.device,
            "is_half": not args.fp32 and args.device == "cuda",
            "version": "v5turbo",
            "t2s_weights_path": os.path.join(base, "s1v3.ckpt"),
            "vits_weights_path": os.path.join(base, "gsv-v5-pretrained", "s2Gv5turbo.pth"),
            "bert_base_path": os.path.join(base, "chinese-roberta-wwm-ext-large"),
            "cnhuhbert_base_path": os.path.join(base, "chinese-hubert-base"),
        }
    }

    log.info("loading GPT-SoVITS V5 Turbo on %s", args.device)
    from TTS_infer_pack.TTS import TTS, TTS_Config

    tts_pipeline = TTS(TTS_Config(configs))
    log.info("GPT-SoVITS V5 Turbo ready")

    # Warm the prompt cache and CUDA kernels with a representative utterance.
    try:
        with infer_lock:
            for _ in tts_pipeline.run(
                {
                    "text": "你好，欢迎使用本地语音合成服务。",
                    "text_lang": "zh",
                    "ref_audio_path": STELLA_WAV,
                    "prompt_text": STELLA_PROMPT,
                    "prompt_lang": "zh",
                    "text_split_method": "cut1",
                    "batch_size": 1,
                    "seed": 1,
                    "sample_steps": 4,
                    "streaming_mode": False,
                    "fragment_interval": 0.0,
                    "parallel_infer": True,
                    "use_cuda_graph": False,
                    "use_flash_attention": False,
                }
            ):
                pass
        log.info("warmup complete")
    except Exception:
        log.exception("warmup failed; first request may be slower")

    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
