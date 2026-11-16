"""Small local replacement for GPT-SoVITS's fast_langdetect-based segmenter.

The deployment always supplies an explicit target language (zh/ja/en), so a
regex splitter for Latin runs is enough. This avoids the Windows-hostile
fast_langdetect/split_lang dependencies.
"""

import re

LATIN_RE = re.compile(r"[A-Za-z][A-Za-z0-9\s.,!?;:'\"()\-]*")
KANA_RE = re.compile(r"[\u3041-\u3096\u30a1-\u30fa]")
HANGUL_RE = re.compile(r"[\u1100-\u11ff\u3130-\u318f\uac00-\ud7af]")


class LangSegmenter:
    DEFAULT_LANG_MAP = {
        "zh": "zh",
        "yue": "zh",
        "wuu": "zh",
        "zh-cn": "zh",
        "zh-tw": "x",
        "ko": "ko",
        "ja": "ja",
        "en": "en",
    }

    @staticmethod
    def getTexts(text, default_lang=""):
        chunks = []
        cursor = 0
        for match in LATIN_RE.finditer(text):
            if match.start() > cursor:
                chunks.append(("", text[cursor:match.start()]))
            chunks.append(("en", match.group(0)))
            cursor = match.end()
        if cursor < len(text):
            chunks.append(("", text[cursor:]))

        result = []
        for lang, value in chunks:
            if not value:
                continue
            if lang == "en":
                detected = "en"
            elif default_lang:
                detected = default_lang
            elif KANA_RE.search(value):
                detected = "ja"
            elif HANGUL_RE.search(value):
                detected = "ko"
            elif re.search(r"[\u3400-\u4dbf\u4e00-\u9fff]", value):
                detected = "zh"
            else:
                detected = "en"
            if result and result[-1]["lang"] == detected:
                result[-1]["text"] += value
            else:
                result.append({"lang": detected, "text": value})
        return result
