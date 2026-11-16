import { create } from 'zustand'
import type { TtsEmotion, TtsVoice } from './api/speech.js'
import { normalizeLocation, type UserLocation } from './location.js'

export type Locale = 'zh-CN' | 'en-US'

/** 主题偏好；默认跟随系统 */
export type ThemePreference = 'light' | 'dark' | 'system'

/** 默认模型与可选模型列表；用户可在设置中获取或添加自定义模型 */
export const DEFAULT_MODEL = 'deepseek-flash'
export const DEFAULT_MODELS = ['deepseek-flash', 'deepseek-v4-pro']

/**
 * 本机设置，存 localStorage：分类 API Key、语言、主题、位置、朗读音色、深度思考与智能搜索开关、
 * 侧边栏收起状态、分类接口地址与模型选择。
 */
type Settings = {
  /** 三类请求各自的 Key；搜索/语音为空时回退到 LLM Key */
  llmApiKey: string | null
  searchApiKey: string | null
  speechApiKey: string | null
  locale: Locale
  themePreference: ThemePreference
  location: UserLocation | null
  ttsVoice: TtsVoice
  ttsEmotion: TtsEmotion
  thinking: boolean
  search: boolean
  siderCollapsed: boolean
  /** 以下地址为完整路径前缀，null 表示使用构建期默认值 */
  llmBaseUrl: string | null
  searchBaseUrl: string | null
  speechBaseUrl: string | null
  /** 当前选中的模型与可选择的模型 ID 列表 */
  model: string
  models: string[]
}

/** 按账号保存在服务器上的网络设置 */
export type NetworkSettings = Pick<
  Settings,
  | 'llmBaseUrl'
  | 'searchBaseUrl'
  | 'speechBaseUrl'
  | 'llmApiKey'
  | 'searchApiKey'
  | 'speechApiKey'
>

const KEYS = {
  llmApiKey: 'chat-demo:api-key',
  searchApiKey: 'chat-demo:search-api-key',
  speechApiKey: 'chat-demo:speech-api-key',
  locale: 'chat-demo:locale',
  location: 'chat-demo:location',
  themePreference: 'chat-demo:theme-preference',
  ttsVoice: 'chat-demo:tts-voice',
  ttsEmotion: 'chat-demo:tts-emotion',
  thinking: 'chat-demo:thinking',
  search: 'chat-demo:search',
  siderCollapsed: 'chat-demo:sider-collapsed',
  llmBaseUrl: 'chat-demo:llm-base-url',
  searchBaseUrl: 'chat-demo:search-base-url',
  speechBaseUrl: 'chat-demo:speech-base-url',
  model: 'chat-demo:model',
  models: 'chat-demo:models',
  /** 本机网络设置所属的账号，用于避免把上一个账号的 Key 迁移给另一个账号 */
  networkOwner: 'chat-demo:network-owner',
} as const

const read = (key: string) => {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

const write = (key: string, value: string | null) => {
  try {
    if (value === null) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
  } catch {
    // 存储不可用时只在内存中生效
  }
}

/**
 * 接口地址为完整路径前缀（如 https://api.deepseek.com/anthropic/v1）。
 * 只接受不含用户名、查询串与片段的 http(s) 地址；空值或非法值返回 null，表示使用默认地址。
 */
export const normalizeBaseUrl = (value: string | null): string | null => {
  const trimmed = value?.trim() ?? ''
  if (!trimmed) {
    return null
  }
  try {
    const url = new URL(trimmed)
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    ) {
      return null
    }
    return trimmed.replace(/\/+$/, '')
  } catch {
    return null
  }
}

const initialBaseUrl = (key: string): string | null => normalizeBaseUrl(read(key))

/** 位置选择模式跟随浏览器语言，与应用内选择的界面语言无关。 */
export const isChineseBrowser = (): boolean =>
  (typeof navigator === 'undefined' ? 'zh' : navigator.language).toLowerCase().startsWith('zh')

const initialLocale = (): Locale => {
  const saved = read(KEYS.locale)
  if (saved === 'zh-CN' || saved === 'en-US') {
    return saved
  }
  return isChineseBrowser() ? 'zh-CN' : 'en-US'
}

const initialLocation = (): UserLocation | null => {
  try {
    const location = normalizeLocation(JSON.parse(read(KEYS.location) ?? 'null'))
    if (!location) {
      return null
    }
    return isChineseBrowser()
      ? location.region === 'CN' && location.province
        ? location
        : null
      : { region: location.region }
  } catch {
    return null
  }
}

/** 未保存过时跟随系统 */
const initialThemePreference = (): ThemePreference => {
  const saved = read(KEYS.themePreference)
  return saved === 'light' || saved === 'dark' ? saved : 'system'
}

/** 未保存过时使用服务端默认音色 stella（海星） */
const initialTtsVoice = (): TtsVoice => (read(KEYS.ttsVoice) === 'tide' ? 'tide' : 'stella')

const TTS_EMOTIONS: readonly TtsEmotion[] = [
  'auto',
  'slow',
  'gentle',
  'happy',
  'sad',
  'serious',
  'excited',
  'dynamic',
]

const initialTtsEmotion = (): TtsEmotion => {
  const saved = read(KEYS.ttsEmotion)
  return saved !== null && TTS_EMOTIONS.includes(saved as TtsEmotion)
    ? (saved as TtsEmotion)
    : 'auto'
}

const initialApiKey = (key: string): string | null => read(key)?.trim() || null

const initialModel = (): string => read(KEYS.model)?.trim() || DEFAULT_MODEL

/** 读取模型列表：只保留非空字符串并去重；始终包含当前模型 */
const initialModels = (model: string): string[] => {
  let saved: string[] = []
  try {
    const parsed: unknown = JSON.parse(read(KEYS.models) ?? 'null')
    if (Array.isArray(parsed)) {
      saved = parsed.flatMap((item) =>
        typeof item === 'string' && item.trim() ? [item.trim()] : []
      )
    }
  } catch {
    // 非 JSON 或格式异常时使用默认列表
  }
  const models = [...new Set(saved)]
  return models.length > 0
    ? models.includes(model)
      ? models
      : [model, ...models]
    : [...new Set([model, ...DEFAULT_MODELS])]
}

export const useSettings = create<Settings>(() => {
  const model = initialModel()
  return {
    llmApiKey: initialApiKey(KEYS.llmApiKey),
    searchApiKey: initialApiKey(KEYS.searchApiKey),
    speechApiKey: initialApiKey(KEYS.speechApiKey),
    locale: initialLocale(),
    location: initialLocation(),
    themePreference: initialThemePreference(),
    ttsVoice: initialTtsVoice(),
    ttsEmotion: initialTtsEmotion(),
    thinking: read(KEYS.thinking) === 'true',
    search: read(KEYS.search) === 'true',
    siderCollapsed: read(KEYS.siderCollapsed) === 'true',
    llmBaseUrl: initialBaseUrl(KEYS.llmBaseUrl),
    searchBaseUrl: initialBaseUrl(KEYS.searchBaseUrl),
    speechBaseUrl: initialBaseUrl(KEYS.speechBaseUrl),
    model,
    models: initialModels(model),
  }
})

export const setLocale = (locale: Locale) => {
  write(KEYS.locale, locale)
  useSettings.setState({ locale })
}

export const setLocation = (value: UserLocation | null) => {
  const location = normalizeLocation(value)
  write(KEYS.location, location ? JSON.stringify(location) : null)
  useSettings.setState({ location })
}

export const setThemePreference = (themePreference: ThemePreference) => {
  write(KEYS.themePreference, themePreference)
  useSettings.setState({ themePreference })
}

export const setTtsVoice = (ttsVoice: TtsVoice) => {
  write(KEYS.ttsVoice, ttsVoice)
  useSettings.setState({ ttsVoice })
}

export const setTtsEmotion = (ttsEmotion: TtsEmotion) => {
  write(KEYS.ttsEmotion, ttsEmotion)
  useSettings.setState({ ttsEmotion })
}

const saveApiKey = (key: string, value: string | null): string | null => {
  const apiKey = value?.trim() || null
  write(key, apiKey)
  return apiKey
}

export const setLlmApiKey = (value: string | null) => {
  const llmApiKey = saveApiKey(KEYS.llmApiKey, value)
  useSettings.setState({ llmApiKey })
  scheduleNetworkSave()
}

export const setSearchApiKey = (value: string | null) => {
  const searchApiKey = saveApiKey(KEYS.searchApiKey, value)
  useSettings.setState({ searchApiKey })
  scheduleNetworkSave()
}

export const setSpeechApiKey = (value: string | null) => {
  const speechApiKey = saveApiKey(KEYS.speechApiKey, value)
  useSettings.setState({ speechApiKey })
  scheduleNetworkSave()
}

/** 请求时读取的 Key：搜索/语音为空时沿用 LLM Key */
export const getLlmApiKey = () => useSettings.getState().llmApiKey ?? ''

export const getSearchApiKey = () => {
  const { searchApiKey, llmApiKey } = useSettings.getState()
  return searchApiKey || llmApiKey || ''
}

export const getSpeechApiKey = () => {
  const { speechApiKey, llmApiKey } = useSettings.getState()
  return speechApiKey || llmApiKey || ''
}

export const setModel = (model: string) => {
  const value = model.trim()
  if (!value) {
    return
  }
  write(KEYS.model, value)
  useSettings.setState({ model: value })
}

/** 添加自定义或获取到的模型，并设为当前模型；重复添加只切换模型 */
export const addModel = (model: string) => {
  const value = model.trim()
  if (!value) {
    return
  }
  const { models } = useSettings.getState()
  const next = models.includes(value) ? models : [...models, value]
  write(KEYS.models, JSON.stringify(next))
  write(KEYS.model, value)
  useSettings.setState({ models: next, model: value })
}

export const setThinking = (thinking: boolean) => {
  write(KEYS.thinking, String(thinking))
  useSettings.setState({ thinking })
}

export const setSearch = (search: boolean) => {
  write(KEYS.search, String(search))
  useSettings.setState({ search })
}

export const setSiderCollapsed = (siderCollapsed: boolean) => {
  write(KEYS.siderCollapsed, String(siderCollapsed))
  useSettings.setState({ siderCollapsed })
}

export const setLlmBaseUrl = (llmBaseUrl: string | null) => {
  const value = normalizeBaseUrl(llmBaseUrl)
  write(KEYS.llmBaseUrl, value)
  useSettings.setState({ llmBaseUrl: value })
  scheduleNetworkSave()
}

export const setSearchBaseUrl = (searchBaseUrl: string | null) => {
  const value = normalizeBaseUrl(searchBaseUrl)
  write(KEYS.searchBaseUrl, value)
  useSettings.setState({ searchBaseUrl: value })
  scheduleNetworkSave()
}

export const setSpeechBaseUrl = (speechBaseUrl: string | null) => {
  const value = normalizeBaseUrl(speechBaseUrl)
  write(KEYS.speechBaseUrl, value)
  useSettings.setState({ speechBaseUrl: value })
  scheduleNetworkSave()
}

const readNetworkSettings = (): NetworkSettings => {
  const state = useSettings.getState()
  return {
    llmBaseUrl: state.llmBaseUrl,
    searchBaseUrl: state.searchBaseUrl,
    speechBaseUrl: state.speechBaseUrl,
    llmApiKey: state.llmApiKey,
    searchApiKey: state.searchApiKey,
    speechApiKey: state.speechApiKey,
  }
}

const hasNetworkValues = (settings: NetworkSettings) =>
  Object.values(settings).some((value) => value !== null && value !== '')

const applyNetworkSettings = (settings: NetworkSettings) => {
  const llmApiKey = saveApiKey(KEYS.llmApiKey, settings.llmApiKey)
  const searchApiKey = saveApiKey(KEYS.searchApiKey, settings.searchApiKey)
  const speechApiKey = saveApiKey(KEYS.speechApiKey, settings.speechApiKey)
  const llmBaseUrl = normalizeBaseUrl(settings.llmBaseUrl)
  const searchBaseUrl = normalizeBaseUrl(settings.searchBaseUrl)
  const speechBaseUrl = normalizeBaseUrl(settings.speechBaseUrl)
  write(KEYS.llmBaseUrl, llmBaseUrl)
  write(KEYS.searchBaseUrl, searchBaseUrl)
  write(KEYS.speechBaseUrl, speechBaseUrl)
  useSettings.setState({
    llmApiKey,
    searchApiKey,
    speechApiKey,
    llmBaseUrl,
    searchBaseUrl,
    speechBaseUrl,
  })
}

export const clearNetworkSettings = () => {
  if (networkSaveTimer !== 0) {
    window.clearTimeout(networkSaveTimer)
    networkSaveTimer = 0
  }
  applyNetworkSettings({
    llmBaseUrl: null,
    searchBaseUrl: null,
    speechBaseUrl: null,
    llmApiKey: null,
    searchApiKey: null,
    speechApiKey: null,
  })
  write(KEYS.networkOwner, null)
}

let networkSaveTimer = 0
let networkSaveChain: Promise<void> = Promise.resolve()

const saveNetworkSettings = (settings: NetworkSettings): Promise<void> => {
  networkSaveChain = networkSaveChain.catch(() => undefined).then(async () => {
    const response = await fetch('/api/settings/network', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ settings }),
    })
    if (!response.ok) {
      throw new Error(`保存网络设置失败：HTTP ${response.status}`)
    }
  })
  return networkSaveChain
}

const scheduleNetworkSave = () => {
  if (networkSaveTimer !== 0) {
    window.clearTimeout(networkSaveTimer)
  }
  networkSaveTimer = window.setTimeout(() => {
    networkSaveTimer = 0
    void saveNetworkSettings(readNetworkSettings()).catch(() => undefined)
  }, 300)
}

/**
 * 登录后加载服务器上的网络设置。服务器没有记录时，把本机已有设置作为首次迁移上传：
 * 只允许没有归属（旧版本留下的）或归属同一账号的本机设置迁移，避免会话过期后
 * 在同一个浏览器登录另一个账号时继承上一个账号的 Key。
 */
export const loadRemoteNetworkSettings = async (currentUsername: string): Promise<void> => {
  const owner = read(KEYS.networkOwner)
  // 登录的账号与上次使用本机网络设置的账号不同时，先清空再请求服务器；
  // 即使请求失败，也不会让新账号看到上一个账号的 Key。
  if (owner !== null && owner !== currentUsername) {
    clearNetworkSettings()
  }
  try {
    const response = await fetch('/api/settings/network', { credentials: 'same-origin' })
    if (!response.ok) {
      return
    }
    const body = (await response.json()) as { settings: NetworkSettings | null }
    if (body.settings) {
      applyNetworkSettings(body.settings)
      write(KEYS.networkOwner, currentUsername)
      return
    }
    const local = readNetworkSettings()
    if (hasNetworkValues(local)) {
      await saveNetworkSettings(local).catch(() => undefined)
    } else {
      clearNetworkSettings()
    }
    write(KEYS.networkOwner, currentUsername)
  } catch {
    // 离线或服务暂不可用时保留本机设置
  }
}
