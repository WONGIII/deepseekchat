"""M5-TTS ONNX HTTP service for the chat-demo frontend.

Exposes the same /tts protocol as the CosyVoice adapter:
POST JSON {text, voice_id, format, filter_markdown, language?} -> 24 kHz mono PCM.

M5-TTS is used in voice-cloning mode. The reference audio and its exact transcript
are defined below. The ONNX model is downloaded automatically from Hugging Face.
"""

import argparse
import glob
import logging
import os
import re
import sys
import threading
import types

import cn2an
import numpy as np
import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(ROOT, "repo")
COSY = os.path.abspath(os.path.join(ROOT, "..", "cosyvoice3"))

# ONNX Runtime GPU loads cuDNN/cuBLAS DLLs from the Python nvidia packages at runtime.
# Add them before importing onnxruntime; otherwise cudnn_engines_tensor_ir cannot be found.
NVIDIA_ROOT = os.path.abspath(
    os.path.join(os.path.dirname(sys.executable), "..", "Lib", "site-packages", "nvidia")
)
if os.path.isdir(NVIDIA_ROOT):
    for bin_dir in glob.glob(os.path.join(NVIDIA_ROOT, "*", "bin")):
        os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
        if hasattr(os, "add_dll_directory"):
            os.add_dll_directory(bin_dir)

TARGET_SAMPLE_RATE = 24000
M5_DEFAULT_WAV = os.path.join(REPO, "assets", "aslp_default_voice.wav")
M5_DEFAULT_PROMPT = "猪携带该疾病，然后通过蚊子传染给人类。"

STELLA_WAV = os.path.join(COSY, "voices", "stella.wav")
STELLA_PROMPT = (
    "是想聊点让人心跳加快的，还是想让这声你好变成喘息的？"
    "嗯，你说嘛，人家都听你的，爸爸。"
)

VOICES = {
    "stella": (
        STELLA_WAV if os.path.exists(STELLA_WAV) else M5_DEFAULT_WAV,
        STELLA_PROMPT if os.path.exists(STELLA_WAV) else M5_DEFAULT_PROMPT,
    ),
    "tide": (M5_DEFAULT_WAV, M5_DEFAULT_PROMPT),
}

HIRAGANA_RE = re.compile(r"[\u3041-\u3096\u309D-\u309F]")
KATAKANA_RE = re.compile(r"[\u30A1-\u30FA\u30FD-\u30FF]")
HANGUL_RE = re.compile(r"[\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]")
CHINESE_RE = re.compile(r"[\u3400-\u4DBF\u4E00-\u9FFF]")
SPEAKABLE_RE = re.compile(
    r"[0-9A-Za-z\u3041-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uAC00-\uD7AF]"
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("m5-tts")

model = None
provider_name = "cpu"
infer_lock = threading.Lock()


def _chinese_number(value):
    try:
        return cn2an.an2cn(value)
    except Exception:
        return value


def _has_chinese_near(text, start, end, radius=12):
    return bool(re.search(r"[\u4e00-\u9fff]", text[max(0, start - radius) : end + radius]))


def normalize_chinese(text):
    """Chinese number/range reading; punctuation and symbols are preserved."""

    def year_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        year = match.group(1)
        return "".join("零一二三四五六七八九"[int(ch)] for ch in year) + "年"

    text = re.sub(r"((?:19|20)\d{2})\s*年", year_repl, text)

    def range_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        start, end = _chinese_number(match.group(1)), _chinese_number(match.group(2))
        unit = (match.group(3) or "").strip()
        if unit in ("℃", "°C", "摄氏度"):
            return f"{start}到{end}摄氏度"
        if unit == "%":
            return f"百分之{start}到{end}"
        if unit:
            return f"{start}到{end}{unit}"
        return f"{start}到{end}"

    text = re.sub(
        r"(\d+(?:\.\d+)?)\s*[~～]\s*(\d+(?:\.\d+)?)(\s*(?:℃|°C|摄氏度|%|度))?",
        range_repl,
        text,
    )

    def temp_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return _chinese_number(match.group(1)) + "摄氏度"

    text = re.sub(r"(\d+(?:\.\d+)?)\s*(?:℃|°C)", temp_repl, text)

    def percent_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return "百分之" + _chinese_number(match.group(1))

    text = re.sub(r"(\d+(?:\.\d+)?)\s*%", percent_repl, text)

    def measure_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return _chinese_number(match.group(1)) + match.group(2)

    return re.sub(
        r"(\d+(?:\.\d+)?)\s*(个|人|岁|次|倍|天|小时|分钟|秒|米|公里|千克|公斤|元|块|万|亿|度)",
        measure_repl,
        text,
    )


def normalize_text(text, lang=None):
    if lang in (None, "Chinese"):
        return normalize_chinese(text)
    return text


# infer_onnx.py imports text_normalization lazily; provide a local implementation
# so M5 can run on Windows without WeTextProcessing/pynini.
_tn = types.ModuleType("text_normalization")
_tn.normalize_text = normalize_text
sys.modules["text_normalization"] = _tn
sys.path.insert(0, REPO)

from infer_onnx import M5Onnx  # noqa: E402


def sanitize_m5_text(text):
    """M5 tokenizer mispronounces decorative glyphs; convert them to safe pauses."""
    text = re.sub(
        r"[\U0001F000-\U0001FAFF\u2600-\u27BF\u2665\u2661\u2764\u2660-\u2667\u266A-\u266F\u2763]\uFE0F?",
        "",
        text,
    )
    text = text.replace("～", "，").replace("~", "，").replace("♡", "").replace("♥", "")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


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
        return "Japanese"
    if HANGUL_RE.search(text):
        return "Korean"
    if CHINESE_RE.search(text):
        return "Chinese"
    return "English"


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
    return b"\x00\x00" * int(TARGET_SAMPLE_RATE * ms / 1000)


def to_pcm16(samples):
    pcm = np.asarray(samples, dtype=np.float32)
    pcm = np.clip(pcm, -1.0, 1.0)
    return (pcm * 32767).astype(np.int16).tobytes()


def cache_reference(model_obj):
    """M5 re-encodes the reference for every generate(); cache it for multi-sentence calls."""
    original = model_obj.encode_reference
    cache = {}
    lock = threading.Lock()

    def cached(audio):
        key = hash(np.asarray(audio, dtype=np.float32).tobytes())
        with lock:
            codes = cache.get(key)
        if codes is None:
            codes = original(audio)
            with lock:
                cache[key] = codes
        return codes

    model_obj.encode_reference = cached


def generate_pcm(text, voice_id, filter_markdown, language_override):
    if filter_markdown:
        text = clean_markdown(text)
    text = sanitize_m5_text(text)
    voice_path, prompt_text = VOICES.get(voice_id, VOICES["stella"])
    sentences = [s for s in split_sentences(text) if SPEAKABLE_RE.search(s)]

    prepared = []
    for raw in sentences:
        if language_override:
            lang = language_override
        else:
            lang = detect_language(raw)
        prepared.append((raw, lang))

    for index, (sentence, lang) in enumerate(prepared):
        log.info("m5 synthesize stella [%s]: %s", lang, sentence[:60])
        with infer_lock:
            for pcm in model.generate(
                sentence,
                voice_path,
                prompt_text,
                lang=lang,
                stream=True,
                seed=1234 + index,
            ):
                yield to_pcm16(pcm)
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
        "status": "ok" if model is not None else "loading",
        "model": "M5-TTS-50M-ONNX",
        "engine": "m5",
        "execution_provider": provider_name,
        "sample_rate": TARGET_SAMPLE_RATE,
        "voices": list(VOICES),
        "languages": ["Chinese", "English", "Japanese", "Korean"],
    }


@app.post("/tts")
@app.post("/v1/tools/tts")
async def tts(request: Request):
    if model is None:
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
    lang_map = {
        "auto": None,
        "zh": "Chinese",
        "chinese": "Chinese",
        "ja": "Japanese",
        "japanese": "Japanese",
        "en": "English",
        "english": "English",
        "ko": "Korean",
        "korean": "Korean",
    }
    language_override = lang_map.get(requested, None)

    return StreamingResponse(
        generate_pcm(text, voice, filter_markdown, language_override),
        media_type="audio/pcm",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "X-TTS-Sample-Rate": str(TARGET_SAMPLE_RATE),
        },
    )


def main():
    global model, provider_name
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--threads", type=int, default=8)
    parser.add_argument("--device", choices=("cpu", "cuda"), default="cuda")
    parser.add_argument("--device-id", type=int, default=0)
    parser.add_argument("--model", default="ASLP-lab/M5-TTS-50M-ONNX")
    parser.add_argument("--cache-dir", default=None)
    args = parser.parse_args()

    log.info("loading M5-TTS ONNX model on %s (threads=%s)", args.device, args.threads)
    try:
        model = M5Onnx(
            model=args.model,
            device=args.device,
            device_id=args.device_id,
            threads=args.threads,
            cache_dir=args.cache_dir,
        )
        provider_name = args.device
    except Exception:
        if args.device != "cuda":
            raise
        log.exception("CUDA M5 ONNX load failed, falling back to CPU")
        model = M5Onnx(model=args.model, device="cpu", threads=args.threads, cache_dir=args.cache_dir)
        provider_name = "cpu"
    cache_reference(model)
    log.info("M5-TTS ready (provider=%s, voices=%s)", provider_name, list(VOICES))
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
