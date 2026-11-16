import { ApiError, request } from './client.js'
import { getSpeechBaseUrl } from './endpoints.js'

/**
 * 语音接口：
 * - ASR：POST /v1/tools/asr，multipart 上传 WAV / Ogg Opus，一次返回全文
 * - TTS：POST /v1/tools/tts，返回音频字节流（产出首帧后即返回 200）
 * 鉴权都用 Authorization: Bearer。
 */

export type TtsVoice = 'stella' | 'tide'

/** 朗读语气：auto 跟随参考音频，dynamic 按句子内容动态切换语气，其余通过服务端 instruct 指令控制 */
export type TtsEmotion =
  | 'auto'
  | 'slow'
  | 'gentle'
  | 'happy'
  | 'sad'
  | 'serious'
  | 'excited'
  | 'dynamic'

/** TTS 输出的默认裸 PCM 参数：24kHz、单声道、16-bit 小端 */
export const TTS_SAMPLE_RATE = 24000

/** 语音合成结果：PCM 流 + 服务端实际采样率（如 GPT-SoVITS 为 48kHz） */
export type SynthesizedSpeech = {
  stream: ReadableStream<Uint8Array>
  sampleRate: number
}

/** 语音识别：audio 为 WAV（单声道 16-bit PCM）或 Ogg Opus，时长不超过 120 秒、大小不超过 8 MiB */
export const transcribe = async (
  apiKey: string,
  audio: Blob,
  signal?: AbortSignal
): Promise<string> => {
  const form = new FormData()
  form.append('file', audio, 'recording.wav')
  const response = await request(`${getSpeechBaseUrl()}/asr`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}` },
    body: form,
    signal,
  })
  const raw = await response.text()
  let body: unknown
  try {
    body = JSON.parse(raw)
  } catch {
    throw new ApiError('protocol', raw)
  }
  if (
    typeof body !== 'object' ||
    body === null ||
    !('text' in body) ||
    typeof body.text !== 'string'
  ) {
    throw new ApiError('protocol', raw)
  }
  return body.text
}

/** 语音合成：返回裸 PCM 字节流；服务端默认过滤代码块、表格、引用标记与长公式 */
export const synthesize = async (
  apiKey: string,
  text: string,
  voice: TtsVoice,
  emotion: TtsEmotion,
  signal: AbortSignal
): Promise<SynthesizedSpeech> => {
  const response = await request(`${getSpeechBaseUrl()}/tts`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      text,
      voice_id: voice,
      format: 'pcm',
      filter_markdown: true,
      emotion,
    }),
    signal,
  })
  if (!response.body) {
    throw new ApiError('protocol', await response.text())
  }
  const reported = Number(response.headers.get('X-TTS-Sample-Rate'))
  const sampleRate =
    Number.isFinite(reported) && reported >= 8000 && reported <= 96000
      ? reported
      : TTS_SAMPLE_RATE
  return { stream: response.body, sampleRate }
}
