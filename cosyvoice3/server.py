import argparse
import logging
import os
import re
import sys
import tempfile
import threading

import num2words
import onnxruntime
import aiohttp
import requests
import torch
import torchaudio
import whisper
import cn2an
from sudachipy import dictionary as sudachi_dictionary
from sudachipy import tokenizer as sudachi_tokenizer

# CosyVoice 在 CUDA 可用时要求 CUDAExecutionProvider；CPU 版 onnxruntime 没有该 provider，
# 这里过滤成实际可用的 provider，避免模型加载失败。
_orig_inference_session = onnxruntime.InferenceSession


def _safe_inference_session(*args, **kwargs):
    providers = kwargs.get("providers")
    if providers:
        available = set(onnxruntime.get_available_providers())
        kwargs["providers"] = [p for p in providers if p in available] or [
            "CPUExecutionProvider"
        ]
    return _orig_inference_session(*args, **kwargs)


onnxruntime.InferenceSession = _safe_inference_session

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(ROOT, "repo")
sys.path.insert(0, REPO)
sys.path.insert(0, os.path.join(REPO, "third_party", "Matcha-TTS"))

from fastapi import FastAPI, File, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
import uvicorn

from cosyvoice.cli.cosyvoice import AutoModel

TARGET_SAMPLE_RATE = 24000
CUSTOM_STELLA_WAV = os.path.join(ROOT, "voices", "stella.wav")
STELLA_PROMPT_WAV = (
    CUSTOM_STELLA_WAV
    if os.path.exists(CUSTOM_STELLA_WAV)
    else os.path.join(REPO, "asset", "zero_shot_prompt.wav")
)
CUSTOM_TIDE_WAV = os.path.join(ROOT, "voices", "tide.wav")
MALE_PROMPT_WAV = (
    CUSTOM_TIDE_WAV
    if os.path.exists(CUSTOM_TIDE_WAV)
    else os.path.join(REPO, "asset", "cross_lingual_prompt.wav")
)
# CosyVoice3 文本/提示中必须包含该控制标记
EOS_PREFIX = "You are a helpful assistant.<|endofprompt|>"
# 自定义参考音频及对应文字稿（Whisper small 识别，保留原有语气词）；没有自定义音频时使用官方文字稿
CUSTOM_STELLA_PROMPT_TEXT = (
    EOS_PREFIX
    + "香港今日11月17日大致多雲，日間部分時間有陽光。"
)
STELLA_PROMPT_TEXT = (
    CUSTOM_STELLA_PROMPT_TEXT
    if os.path.exists(CUSTOM_STELLA_WAV)
    else EOS_PREFIX + "希望你以后能够做的比我还好呦。"
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("cosyvoice-tts")

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["X-TTS-Sample-Rate"],
)

cosyvoice = None
sample_rate = TARGET_SAMPLE_RATE
infer_lock = threading.Lock()
stella_spk_id = ""
male_spk_id = ""
# 默认开启模型流式输出：首句延迟更低；可用 --no-stream 切到质量优先模式
STREAMING = True
# TTS 引擎：cosyvoice 本地加载；m5 转发 M5-TTS；gsv 转发 GPT-SoVITS V5 Turbo
TTS_ENGINE = "cosyvoice"
M5_URL = "http://127.0.0.1:8001"
GSV_URL = "http://127.0.0.1:8002"
asr_model = None
asr_lock = threading.Lock()
ASR_MODEL_NAME = ""

VOICE_IDS = ("stella", "tide")
EMOTION_INSTRUCTIONS = {
    "slow": "请用自然、平稳、语速稍慢、音量自然的语气说这句话。",
    "gentle": "请用温柔、柔和、音量自然的语气说这句话，不要喊叫，不要破音。语气词和省略号要自然带出，不要平读。",
    "happy": "请用轻快、明亮、音量自然的语气说这句话，不要喊叫，不要破音。语气词和省略号要自然带出，不要平读。",
    "sad": "请用低沉、悲伤、音量自然的语气说这句话。",
    "serious": "请用严肃、平稳、音量自然的语气说这句话。",
    "excited": "请用稍微兴奋、轻快、音量自然的语气说这句话，不要喊叫，不要破音。",
    # 动态模式按句子内容选择下列子语气
    "dynamic_soft": "请用轻柔、带气息、像在耳边低语一样的语气，音量自然，不要喊叫。语气词和省略号要自然带出，不要平读。",
    "dynamic_tease": "请用带点疑问和挑逗的语气，音量自然，不要喊叫。语气词要自然带出，不要平读。",
    "dynamic_playful": "请用撒娇、轻快、音量自然的语气，不要喊叫。语气词要自然带出，不要平读。",
    "dynamic_comfort": "请用温柔、平稳、音量自然的语气说这句话。",
    "dynamic_sad": "请用低沉、难过、音量自然的语气说这句话。",
    "dynamic_serious": "请用认真、平稳、音量自然的语气说这句话。",
}
EMOTION_IDS = ("auto", "slow", "gentle", "happy", "sad", "serious", "excited", "dynamic")


def choose_dynamic_emotion(text):
    """动态语气：优先看句尾标点，其次看句中的省略号/心形等标记，避免整段一个调。"""
    tail = text.rstrip(" \t\r\n\"'」』）】")
    tail = tail.rstrip("。")
    if tail.endswith(("？", "?")):
        return "dynamic_tease"
    if tail.endswith(("！", "!")):
        return "dynamic_playful"
    if any(mark in text for mark in ("……", "...", "～", "~", "♥", "♡", "❤")):
        return "dynamic_soft"
    if any(word in text for word in ("难过", "伤心", "哭", "寂寞", "别走", "舍不得")):
        return "dynamic_sad"
    if any(word in text for word in ("不行", "住手", "认真", "警告", "必须")):
        return "dynamic_serious"
    return "dynamic_comfort"


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
    # 只处理 Markdown 删除线；区间号 ~ 和更多标点保留
    text = re.sub(r"~~([^~]*?)~~", r"\1", text)
    text = re.sub(r"[*_#>]+", "", text)
    text = re.sub(r"\$[^$]*\$", "", text)
    # 换行按句号处理；保留表情、符号和重复标点，交给模型表现情绪
    text = text.replace("\n", "。")
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def _chinese_number(value):
    try:
        return cn2an.an2cn(value)
    except Exception:
        return value


def _has_chinese_near(text, start, end, radius=12):
    return bool(re.search(r"[\u4e00-\u9fff]", text[max(0, start - radius) : end + radius]))


def normalize_for_tts(text):
    """把中文语境里的区间、温度、百分比、年份和常见量词转成中文读法"""
    # 年份按逐字读：2026年 -> 二零二六年
    def year_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        year = match.group(1)
        return "".join("零一二三四五六七八九"[int(ch)] for ch in year) + "年"

    text = re.sub(r"((?:19|20)\d{2})\s*年", year_repl, text)

    # 温度/百分比/度数区间：20~22℃ -> 二十到二十二摄氏度
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

    # 单个温度：22℃ -> 二十二摄氏度
    def temp_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return _chinese_number(match.group(1)) + "摄氏度"

    text = re.sub(r"(\d+(?:\.\d+)?)\s*(?:℃|°C)", temp_repl, text)

    # 百分比：20% -> 百分之二十
    def percent_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return "百分之" + _chinese_number(match.group(1))

    text = re.sub(r"(\d+(?:\.\d+)?)\s*%", percent_repl, text)

    # 常见量词：20个 -> 二十个
    def measure_repl(match):
        if not _has_chinese_near(text, match.start(), match.end()):
            return match.group(0)
        return _chinese_number(match.group(1)) + match.group(2)

    text = re.sub(
        r"(\d+(?:\.\d+)?)\s*(个|人|岁|次|倍|天|小时|分钟|秒|米|公里|千克|公斤|元|块|万|亿|度)",
        measure_repl,
        text,
    )
    return text


HIRAGANA_RE = re.compile(r"[\u3041-\u3096\u309D-\u309F]")
KATAKANA_RE = re.compile(r"[\u30A1-\u30FA\u30FD-\u30FF]")
HANGUL_RE = re.compile(r"[\u1100-\u11FF\u3130-\u318F\uAC00-\uD7AF]")
CYRILLIC_RE = re.compile(r"[\u0400-\u04FF]")
CHINESE_RE = re.compile(r"[\u3400-\u4DBF\u4E00-\u9FFF]")
SPEAKABLE_RE = re.compile(
    r"[0-9A-Za-z\u3041-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uAC00-\uD7AF\u0400-\u04FF]"
)

_japanese_tokenizer = None
_japanese_tokenizer_lock = threading.Lock()
JAPANESE_DIGITS = ("ゼロ", "イチ", "ニ", "サン", "ヨン", "ゴ", "ロク", "ナナ", "ハチ", "キュウ")


def detect_language(text):
    """按文字脚本判断目标语言；日语文本通常含平假名/片假名。"""
    if HIRAGANA_RE.search(text) or KATAKANA_RE.search(text):
        return "ja"
    if HANGUL_RE.search(text):
        return "ko"
    if CYRILLIC_RE.search(text):
        return "ru"
    if CHINESE_RE.search(text):
        return "zh"
    return "en"


def _hiragana_to_katakana(text):
    return "".join(
        chr(ord(ch) + 0x60) if "\u3041" <= ch <= "\u3096" else ch for ch in text
    )


def _read_japanese_number(token):
    """数字转日语读音（片假名）；年份等按自然数读，小数逐位读。"""
    try:
        if "." in token:
            whole, frac = token.split(".", 1)
            head = _read_japanese_number(whole) if whole else "ゼロ"
            return head + "テン" + "".join(
                JAPANESE_DIGITS[int(ch)] for ch in frac if ch.isdigit()
            )
        # 007、电话号码等保留逐位读法
        if len(token) > 1 and token.startswith("0"):
            return "".join(JAPANESE_DIGITS[int(ch)] for ch in token if ch.isdigit())
        return _hiragana_to_katakana(
            num2words.num2words(int(token), lang="ja", reading=True)
        )
    except Exception:
        return token


def _normalize_japanese_numbers(text):
    def range_repl(match):
        return (
            _read_japanese_number(match.group(1))
            + "カラ"
            + _read_japanese_number(match.group(2))
            + (match.group(3) or "")
        )

    text = re.sub(
        r"(\d+(?:\.\d+)?)\s*[~～]\s*(\d+(?:\.\d+)?)(\s*(?:℃|°C|%|度))?",
        range_repl,
        text,
    )
    text = re.sub(r"\d+(?:\.\d+)?", lambda m: _read_japanese_number(m.group(0)), text)
    return (
        text.replace("%", "パーセント")
        .replace("％", "パーセント")
        .replace("°C", "ド")
        .replace("℃", "ド")
    )


def japanese_to_katakana(text):
    """CosyVoice3 官方要求日语先转写成片假名；汉字用 Sudachi 读音，英文/符号原样保留。
    词与词之间留空格，官方示例即为此格式，能明显改善日语韵律和清晰度。"""
    global _japanese_tokenizer
    text = _normalize_japanese_numbers(text)
    if _japanese_tokenizer is None:
        with _japanese_tokenizer_lock:
            if _japanese_tokenizer is None:
                _japanese_tokenizer = sudachi_dictionary.Dictionary().tokenizer()
    result = []
    needs_space = False
    for token in _japanese_tokenizer.tokenize(
        text, sudachi_tokenizer.Tokenizer.SplitMode.C
    ):
        surface = token.surface()
        if not surface.strip():
            needs_space = True
            continue
        if re.search(r"[A-Za-z]", surface):
            piece = surface
        elif re.fullmatch(r"[\W_]+", surface):
            # 标点、表情、装饰符号紧贴前文，不额外插空格
            result.append(surface)
            needs_space = False
            continue
        else:
            reading = token.reading_form()
            piece = reading if reading and reading != "キゴウ" else surface
        if needs_space and result:
            result.append(" ")
        result.append(piece)
        needs_space = True
    return "".join(result)


def split_sentences(text, max_len=180):
    parts = re.split(r"(?<=[。！？!?；;])|\n+", text)
    chunks = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        while len(part) > max_len:
            cut = max(part.rfind("，", 0, max_len), part.rfind(",", 0, max_len), part.rfind(" ", 0, max_len))
            if cut <= 0:
                cut = max_len - 1
            chunks.append(part[: cut + 1].strip())
            part = part[cut + 1 :].strip()
        if part:
            chunks.append(part)
    return chunks


def sentence_pause_ms(text, language=None, next_language=None):
    """按句尾标点补静音；语种切换再多留一点，避免硬切。"""
    stripped = text.rstrip(" \t\r\n\"'」』）】")
    ended_period = stripped.endswith("。")
    tail = stripped.rstrip("。")
    if tail.endswith(("……", "…", "...")):
        pause = 500
    elif tail.endswith(("？", "?")):
        pause = 380
    elif tail.endswith(("！", "!")):
        pause = 340
    elif ended_period:
        pause = 300
    elif tail.endswith(("，", ",", "、", "：", ":")):
        pause = 150
    else:
        pause = 90
    if language and next_language and language != next_language:
        pause += 180
    return pause


def silence_pcm(ms):
    return b"\x00\x00" * int(TARGET_SAMPLE_RATE * ms / 1000)


def to_pcm16(speech):
    speech = speech.detach().float().cpu()
    if speech.dim() == 2:
        speech = speech[0]
    if sample_rate != TARGET_SAMPLE_RATE:
        speech = torchaudio.functional.resample(speech, sample_rate, TARGET_SAMPLE_RATE)
    # 情绪指令容易让个别句突然变大/接近满幅；做温和的响度上限，避免听起来像吼叫
    rms = torch.sqrt(torch.mean(speech ** 2)).item()
    if rms > 0.20:
        speech = speech * (0.20 / rms)
    peak = speech.abs().max().item()
    if peak > 0.95:
        speech = speech * (0.95 / peak)
    speech = torch.clamp(speech, -1.0, 1.0)
    return (speech * 32767).to(torch.int16).numpy().tobytes()


def cache_emotion_speakers():
    """复用参考音频特征，只替换 instruct 文本 token，避免每句重复提取 13 秒参考音频"""
    for base_id, prompt_wav in (("stella", STELLA_PROMPT_WAV), ("tide", MALE_PROMPT_WAV)):
        base = cosyvoice.frontend.spk2info.get(base_id)
        if base is None:
            continue
        for key, instruction in EMOTION_INSTRUCTIONS.items():
            instruct = (
                "You are a helpful assistant. "
                + instruction
                + "<|endofprompt|>"
            )
            try:
                tokens = cosyvoice.frontend._extract_text_token(instruct)
                model_input = dict(base)
                model_input["prompt_text"], model_input["prompt_text_len"] = tokens
                cosyvoice.frontend.spk2info[f"{base_id}:{key}"] = model_input
                # 日语目标文本复用中文 in-distribution 指令，避免模型把日语指令当成正文读出来
                cosyvoice.frontend.spk2info[f"{base_id}:ja:{key}"] = model_input
            except Exception:
                log.exception("cache emotion speaker failed: %s %s", base_id, key)


def _cross_lingual_infer(text, voice_id, stream, emotion):
    speed = 0.90 if emotion == "slow" else 1.0
    text = EOS_PREFIX + text
    if voice_id == "tide":
        if male_spk_id:
            return cosyvoice.inference_cross_lingual(
                text,
                "",
                zero_shot_spk_id=male_spk_id,
                stream=stream,
                speed=speed,
                text_frontend=False,
            )
        return cosyvoice.inference_cross_lingual(
            text,
            MALE_PROMPT_WAV,
            stream=stream,
            speed=speed,
            text_frontend=False,
        )
    if stella_spk_id:
        return cosyvoice.inference_cross_lingual(
            text,
            "",
            zero_shot_spk_id=stella_spk_id,
            stream=stream,
            speed=speed,
            text_frontend=False,
        )
    return cosyvoice.inference_cross_lingual(
        text,
        STELLA_PROMPT_WAV,
        stream=stream,
        speed=speed,
        text_frontend=False,
    )


def infer(text, voice_id, stream=True, emotion="auto", language="zh"):
    # 日语：显式情绪/动态语气用中文 instruct2 指令；auto/slow 仍走 cross_lingual
    if language == "ja":
        if emotion == "dynamic":
            emotion = choose_dynamic_emotion(text)
            log.info("dynamic ja tone -> %s: %s", emotion, text[:40])
        if emotion in EMOTION_INSTRUCTIONS and emotion not in ("auto", "slow"):
            prompt_wav = MALE_PROMPT_WAV if voice_id == "tide" else STELLA_PROMPT_WAV
            instruct = (
                "You are a helpful assistant. "
                + EMOTION_INSTRUCTIONS[emotion]
                + "<|endofprompt|>"
            )
            speed = 1.0
            spk_id = f"{voice_id}:ja:{emotion}"
            if spk_id in cosyvoice.frontend.spk2info:
                return cosyvoice.inference_instruct2(
                    text,
                    instruct,
                    "",
                    zero_shot_spk_id=spk_id,
                    stream=stream,
                    speed=speed,
                    text_frontend=False,
                )
            return cosyvoice.inference_instruct2(
                text,
                instruct,
                prompt_wav,
                stream=stream,
                speed=speed,
                text_frontend=False,
            )
        return _cross_lingual_infer(text, voice_id, stream, emotion)
    # 非中文目标语言统一走 cross_lingual：不把中文参考文本送进 LLM，避免中文语调污染
    if language != "zh":
        if emotion in EMOTION_INSTRUCTIONS:
            log.info("emotion %s ignored for %s cross-lingual speech", emotion, language)
        return _cross_lingual_infer(text, voice_id, stream, emotion)
    # 动态模式按当前句子的标点/内容选择子语气
    if emotion == "dynamic":
        emotion = choose_dynamic_emotion(text)
        log.info("dynamic tone -> %s: %s", emotion, text[:40])
    # 情绪/语气走 instruct2：保留同一段参考音色，用文字指令控制语气
    if emotion in EMOTION_INSTRUCTIONS:
        prompt_wav = MALE_PROMPT_WAV if voice_id == "tide" else STELLA_PROMPT_WAV
        instruct = (
            "You are a helpful assistant. "
            + EMOTION_INSTRUCTIONS[emotion]
            + "<|endofprompt|>"
        )
        speed = 0.90 if emotion == "slow" else 1.0
        spk_id = f"{voice_id}:{emotion}"
        if spk_id in cosyvoice.frontend.spk2info:
            return cosyvoice.inference_instruct2(
                text,
                instruct,
                "",
                zero_shot_spk_id=spk_id,
                stream=stream,
                speed=speed,
                text_frontend=False,
            )
        return cosyvoice.inference_instruct2(
            text,
            instruct,
            prompt_wav,
            stream=stream,
            speed=speed,
            text_frontend=False,
        )
    # CosyVoice3 自带文本规范化，关闭 wetext/ttsfrd 前端以避免额外模型下载
    if voice_id == "tide":
        text = EOS_PREFIX + text
        if male_spk_id:
            return cosyvoice.inference_cross_lingual(
                text, "", zero_shot_spk_id=male_spk_id, stream=stream, text_frontend=False
            )
        return cosyvoice.inference_cross_lingual(
            text, MALE_PROMPT_WAV, stream=stream, text_frontend=False
        )
    if stella_spk_id:
        return cosyvoice.inference_zero_shot(
            text, "", "", zero_shot_spk_id=stella_spk_id, stream=stream, text_frontend=False
        )
    return cosyvoice.inference_zero_shot(
        text,
        STELLA_PROMPT_TEXT,
        STELLA_PROMPT_WAV,
        stream=stream,
        text_frontend=False,
    )


def generate_pcm(text, voice_id, filter_markdown, stream, emotion, language="auto"):
    if filter_markdown:
        text = clean_markdown(text)
    items = []
    if language != "auto":
        # 显式指定语言时先整段转换，保留上下文读音
        if language == "ja":
            text = japanese_to_katakana(text)
        elif language == "zh":
            text = normalize_for_tts(text)
        for sentence in split_sentences(text):
            if SPEAKABLE_RE.search(sentence):
                items.append((sentence, language, sentence))
    else:
        # 自动模式按句判断语言：中日混排时中文句走中文，日语假名句走日语
        for raw in split_sentences(text):
            if not SPEAKABLE_RE.search(raw):
                continue
            lang = detect_language(raw)
            sentence = raw
            if lang == "ja":
                sentence = japanese_to_katakana(raw)
            elif lang == "zh":
                sentence = normalize_for_tts(raw)
            items.append((sentence, lang, raw))

    for index, (sentence, lang, raw) in enumerate(items):
        log.info("synthesize %s [%s/%s]: %s", voice_id, emotion, lang, sentence[:60])
        with infer_lock:
            for output in infer(
                sentence, voice_id, stream=stream, emotion=emotion, language=lang
            ):
                yield to_pcm16(output["tts_speech"])
        if index + 1 < len(items):
            next_lang = items[index + 1][1]
            yield silence_pcm(sentence_pause_ms(raw, lang, next_lang))


async def proxy_tts_async(url, body, request):
    """异步转发 PCM；客户端一断开就关闭上游连接，避免 GSV 继续推理。"""
    timeout = aiohttp.ClientTimeout(total=None, sock_connect=10, sock_read=300)
    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.post(f"{url}/tts", json=body) as response:
            if response.status >= 400:
                detail = await response.text()
                raise RuntimeError(f"upstream TTS {response.status}: {detail[:200]}")
            async for chunk in response.content.iter_chunked(4096):
                if await request.is_disconnected():
                    break
                if chunk:
                    yield chunk


@app.get("/health")
def health():
    if TTS_ENGINE in ("m5", "gsv"):
        data = {
            "status": "ok",
            "model": "M5-TTS-50M-ONNX" if TTS_ENGINE == "m5" else "GPT-SoVITS-v5turbo",
            "sample_rate": 48000 if TTS_ENGINE == "gsv" else TARGET_SAMPLE_RATE,
            "voices": VOICE_IDS,
            "emotions": EMOTION_IDS,
            "asr": ASR_MODEL_NAME or None,
            "languages": ("zh", "en", "ja", "ko") if TTS_ENGINE == "m5" else ("zh", "en", "ja"),
            "tts_engine": TTS_ENGINE,
        }
        data["m5_url" if TTS_ENGINE == "m5" else "gsv_url"] = (
            M5_URL if TTS_ENGINE == "m5" else GSV_URL
        )
        return data
    return {
        "status": "ok",
        "model": os.path.basename(MODEL_DIR),
        "sample_rate": sample_rate,
        "voices": VOICE_IDS,
        "emotions": EMOTION_IDS,
        "asr": ASR_MODEL_NAME or None,
        "languages": ("zh", "en", "ja", "ko", "ru"),
    }


@app.post("/asr")
@app.post("/v1/tools/asr")
def asr(file: UploadFile = File(...)):
    """Whisper 语音识别：接收 multipart 的 file 字段，返回 {text}"""
    if asr_model is None:
        return JSONResponse({"error": {"message": "ASR model is not loaded"}}, status_code=503)
    data = file.file.read()
    if not data:
        return JSONResponse({"error": {"message": "audio is empty"}}, status_code=400)
    if len(data) > 16 * 1024 * 1024:
        return JSONResponse({"error": {"message": "audio file too large"}}, status_code=413)
    suffix = os.path.splitext(file.filename or "")[1] or ".wav"
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    tmp_path = tmp.name
    try:
        tmp.write(data)
        tmp.close()
        with asr_lock:
            result = asr_model.transcribe(tmp_path, language=None, beam_size=5)
        text = str(result.get("text") or "").strip()
        log.info("asr result: %s", text[:80])
        return {"text": text}
    except Exception as exc:
        log.exception("asr failed")
        return JSONResponse({"error": {"message": str(exc)}}, status_code=500)
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass


@app.post("/tts")
@app.post("/v1/tools/tts")
async def tts(request: Request):
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"error": {"message": "invalid json"}}, status_code=400)
    text = str(body.get("text") or "").strip()
    if not text:
        return JSONResponse({"error": {"message": "text is empty"}}, status_code=400)
    voice = body.get("voice_id") or "stella"
    if voice not in VOICE_IDS:
        voice = "stella"
    filter_markdown = bool(body.get("filter_markdown", True))
    if TTS_ENGINE in ("m5", "gsv"):
        body["voice_id"] = voice
        body["filter_markdown"] = filter_markdown
        body["stream"] = True
        url = M5_URL if TTS_ENGINE == "m5" else GSV_URL
        sample_rate = 48000 if TTS_ENGINE == "gsv" else TARGET_SAMPLE_RATE
        return StreamingResponse(
            proxy_tts_async(url, body, request),
            media_type="audio/pcm",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
                "X-TTS-Sample-Rate": str(sample_rate),
            },
        )
    stream = bool(body.get("stream", STREAMING))
    emotion = str(body.get("emotion") or "auto")
    if emotion not in EMOTION_IDS:
        emotion = "auto"
    language = str(body.get("language") or "auto").strip().lower().replace("_", "-")
    language = language.split("-", 1)[0]
    if language not in ("auto", "zh", "en", "ja", "ko", "ru"):
        language = "auto"
    return StreamingResponse(
        generate_pcm(text, voice, filter_markdown, stream, emotion, language),
        media_type="audio/pcm",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "X-TTS-Sample-Rate": str(TARGET_SAMPLE_RATE),
        },
    )


def main():
    global cosyvoice, sample_rate, stella_spk_id, male_spk_id, MODEL_DIR, STREAMING
    global asr_model, ASR_MODEL_NAME, TTS_ENGINE, M5_URL, GSV_URL
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument(
        "--model-dir",
        default=os.path.join(REPO, "pretrained_models", "Fun-CosyVoice3-0.5B"),
    )
    parser.add_argument("--fp16", action="store_true", default=True)
    parser.add_argument(
        "--no-stream", action="store_false", dest="stream", help="关闭模型流式输出（质量优先，首句延迟更高）"
    )
    parser.add_argument(
        "--tts-engine",
        choices=("cosyvoice", "m5", "gsv"),
        default="cosyvoice",
        help="TTS 引擎：cosyvoice（默认）、m5 或 gsv",
    )
    parser.add_argument("--m5-url", default="http://127.0.0.1:8001")
    parser.add_argument("--gsv-url", default="http://127.0.0.1:8002")
    parser.add_argument("--asr-model", default="small", help="Whisper 模型：tiny/base/small/medium")
    parser.add_argument("--no-asr", action="store_true", help="不加载 Whisper 识别模型")
    parser.set_defaults(stream=True)
    args = parser.parse_args()
    STREAMING = bool(args.stream)
    TTS_ENGINE = args.tts_engine
    M5_URL = args.m5_url.rstrip("/")
    GSV_URL = args.gsv_url.rstrip("/")
    MODEL_DIR = args.model_dir
    if TTS_ENGINE == "cosyvoice":
        log.info(
            "loading CosyVoice model %s (fp16=%s, streaming=%s)",
            MODEL_DIR,
            args.fp16,
            STREAMING,
        )
        cosyvoice = AutoModel(model_dir=MODEL_DIR, fp16=bool(args.fp16))
        sample_rate = cosyvoice.sample_rate
        log.info("CosyVoice model loaded, sample_rate=%s", sample_rate)
        log.info("stella prompt wav: %s", STELLA_PROMPT_WAV)
        try:
            cosyvoice.add_zero_shot_spk(STELLA_PROMPT_TEXT, STELLA_PROMPT_WAV, "stella")
            stella_spk_id = "stella"
        except Exception:
            log.exception("stella speaker cache failed, fallback to prompt wav each request")
            stella_spk_id = ""
        try:
            cosyvoice.add_zero_shot_spk("", MALE_PROMPT_WAV, "tide")
            male_spk_id = "tide"
        except Exception:
            log.exception("male zero-shot speaker cache failed, fallback to prompt wav each request")
            male_spk_id = ""
        log.info("prompt voices cached")
        cache_emotion_speakers()
        log.info("emotion speakers cached")
    else:
        sample_rate = TARGET_SAMPLE_RATE
        if TTS_ENGINE == "m5":
            log.info("TTS engine: M5-TTS at %s (CosyVoice not loaded)", M5_URL)
        else:
            log.info("TTS engine: GPT-SoVITS at %s (CosyVoice not loaded)", GSV_URL)
    if args.no_asr:
        log.info("ASR disabled")
    else:
        log.info("loading Whisper ASR model %s", args.asr_model)
        try:
            asr_model = whisper.load_model(args.asr_model)
            ASR_MODEL_NAME = args.asr_model
        except Exception:
            log.exception("Whisper GPU load failed, retry on CPU")
            try:
                asr_model = whisper.load_model(args.asr_model, device="cpu")
                ASR_MODEL_NAME = f"{args.asr_model} (cpu)"
            except Exception:
                log.exception("Whisper ASR load failed; /asr will return 503")
                asr_model = None
                ASR_MODEL_NAME = ""
        log.info("ASR ready: %s", ASR_MODEL_NAME or "disabled")
    uvicorn.run(app, host=args.host, port=args.port, log_level="info")


if __name__ == "__main__":
    main()
