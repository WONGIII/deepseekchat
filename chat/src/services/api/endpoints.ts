import { useSettings } from '../settings.js'

/**
 * 运行时接口地址：默认值来自构建期 __API_ORIGIN__，设置里可按请求类别覆盖。
 * 覆盖值是完整路径前缀，客户端只追加 /messages、/files、/search、/asr、/tts、/models。
 */

/** 构建期默认 API 地址（去掉末尾斜杠），见 rspack.config.ts 的 createConfig */
const DEFAULT_API_ORIGIN = __API_ORIGIN__.replace(/\/+$/, '')

/** 默认 LLM（Messages / Files）接口前缀 */
export const DEFAULT_LLM_BASE_URL = `${DEFAULT_API_ORIGIN}/anthropic/v1`
/** 默认独立搜索接口前缀 */
export const DEFAULT_SEARCH_BASE_URL = `${DEFAULT_API_ORIGIN}/v1/tools`
/** 默认语音 ASR / TTS 接口前缀 */
export const DEFAULT_SPEECH_BASE_URL = `${DEFAULT_API_ORIGIN}/v1/tools`
/** 默认模型列表地址；实际按 LLM 地址推导 */
export const DEFAULT_MODELS_URL = `${DEFAULT_API_ORIGIN}/models`

const resolveBaseUrl = (custom: string | null, fallback: string) => {
  const value = custom?.trim()
  return value ? value.replace(/\/+$/, '') : fallback
}

export const getLlmBaseUrl = () =>
  resolveBaseUrl(useSettings.getState().llmBaseUrl, DEFAULT_LLM_BASE_URL)

export const getSearchBaseUrl = () =>
  resolveBaseUrl(useSettings.getState().searchBaseUrl, DEFAULT_SEARCH_BASE_URL)

export const getSpeechBaseUrl = () =>
  resolveBaseUrl(useSettings.getState().speechBaseUrl, DEFAULT_SPEECH_BASE_URL)

/**
 * 模型列表候选地址，按顺序尝试：
 * 1. LLM Base URL + /models（OpenAI 兼容网关，如 https://example.com/v1/models）
 * 2. LLM 地址同源 + /models（DeepSeek 官方，如 https://api.deepseek.com/models）
 */
export const getModelsUrls = () => {
  const base = getLlmBaseUrl()
  const candidates = [`${base}/models`]
  try {
    const origin = `${new URL(base).origin}/models`
    if (origin !== candidates[0]) {
      candidates.push(origin)
    }
  } catch {
    if (DEFAULT_MODELS_URL !== candidates[0]) {
      candidates.push(DEFAULT_MODELS_URL)
    }
  }
  return candidates
}
