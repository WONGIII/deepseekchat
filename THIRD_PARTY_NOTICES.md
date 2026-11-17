# 第三方项目与许可证声明

本仓库包含原创整合代码，也包含若干上游开源项目和第三方依赖。原创部分使用仓库根目录的
[MIT License](LICENSE)；下列项目继续适用各自许可证，不因本仓库的 MIT 许可而改变。

完整许可证文本通常已随上游源码放在对应目录的 `LICENSE`、`NOTICE` 或第三方许可文件中。
如果某个依赖只通过包管理器下载、仓库内没有许可证原文，请以其上游发布包中的许可证为准。

## 主要上游项目

| 项目 | 用途 | 许可证 | 仓库内位置/说明 |
| --- | --- | --- | --- |
| [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo) | 网页界面、交互和部分图标路径的来源 | MIT | `chat/`；原文见 `chat/LICENSE`（Copyright © 2026 DeepSeek） |
| [FunAudioLLM/CosyVoice](https://github.com/FunAudioLLM/CosyVoice) | CosyVoice3 本地 TTS | Apache License 2.0 | `cosyvoice3/repo/`；原文见 `cosyvoice3/repo/LICENSE` |
| [Shivam Mehta / Matcha-TTS](https://github.com/shivammehta25/Matcha-TTS) | CosyVoice 依赖的声学模型组件 | MIT | `cosyvoice3/repo/third_party/Matcha-TTS/LICENSE` |
| [RVC-Boss/GPT-SoVITS](https://github.com/RVC-Boss/GPT-SoVITS) | GPT-SoVITS V5 Turbo 本地 TTS | MIT | `gpt-sovits/repo/`；原文见 `gpt-sovits/repo/LICENSE` |
| [ASLP-lab/M5-TTS](https://github.com/ASLP-lab/M5-TTS) | M5-TTS 试验性本地 TTS | 上游代码与模型分别适用其发布条款 | `m5-tts/repo/`；其中 Qwen3-TTS 12Hz codec 为 Apache-2.0，见 `m5-tts/repo/m5/codec_qwen12hz/LICENSE` 和 `NOTICE` |
| [openai/whisper](https://github.com/openai/whisper) | 本地语音识别（ASR） | MIT | 通过 Python 包安装，模型权重另行下载 |

### GPT-SoVITS 内附第三方组件

GPT-SoVITS 目录还包含 BigVGAN、G2PW、日语分词词典等第三方组件及资源。其许可证文本位于：

- `gpt-sovits/repo/GPT_SoVITS/BigVGAN/LICENSE`
- `gpt-sovits/repo/GPT_SoVITS/BigVGAN/incl_licenses/`
- `gpt-sovits/repo/tools/AP_BWE_main/LICENSE`

这些组件仍归原作者所有，并按各自许可证使用。

## 网页端依赖

`chat/` 使用 pnpm/npm 管理依赖，不会把 `node_modules` 提交到仓库。主要依赖的常见许可证包括：

- React、React DOM、Zustand、clsx 等：MIT
- Markdown/代码高亮、PDF/Office 解析依赖：按其 npm 包声明的许可证（通常为 MIT、Apache-2.0 或 BSD）
- 完整依赖树和许可证可通过 `pnpm licenses list` 或安装后的包内 `LICENSE` 文件查看

## Android 客户端依赖

`android-app/` 使用 Gradle 管理依赖，主要第三方库及许可证如下：

| 依赖 | 许可证 |
| --- | --- |
| AndroidX、Jetpack Compose、Material 3、Material Icons、ExifInterface | Apache License 2.0 |
| Kotlin、kotlinx.coroutines、kotlinx.serialization | Apache License 2.0 |
| OkHttp、Okio | Apache License 2.0 |
| Coil | Apache License 2.0 |
| commonmark-java | BSD 2-Clause License |
| PDFBox-Android | Apache License 2.0 |

App 内“设置 → 开源许可”也展示了对应的许可证摘要。

## 模型权重与商标

本仓库**不包含** CosyVoice3、GPT-SoVITS、M5-TTS、Whisper 的模型权重。下载和使用模型权重时，
还必须遵守对应模型仓库/发布页的附加条款。

DeepSeek 名称、鲸鱼标识及其他官方品牌标识不包含在 chat-demo 的 MIT 许可中，商标权仍归其
权利人所有。本仓库使用中性的 Chat 品牌，不代表与 DeepSeek 官方存在关联或获得其背书。
