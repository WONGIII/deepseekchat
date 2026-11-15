import { ApiError, request } from './client.js'
import { getModelsUrls } from './endpoints.js'

/** 兼容 `{ data: [{ id }] }`、`{ data: ["id"] }` 与直接的字符串数组 */
const readModelId = (value: unknown): string | null => {
  if (typeof value === 'string') {
    return value.trim() || null
  }
  if (typeof value === 'object' && value !== null && 'id' in value) {
    const id = (value as { id: unknown }).id
    return typeof id === 'string' ? id.trim() || null : null
  }
  return null
}

const readModelList = async (
  url: string,
  apiKey: string,
  signal?: AbortSignal
): Promise<string[]> => {
  const response = await request(url, {
    method: 'GET',
    headers: { Authorization: `Bearer ${apiKey}` },
    signal,
  })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new ApiError('protocol', '')
  }
  const list = Array.isArray(body)
    ? body
    : typeof body === 'object' && body !== null && 'data' in body
      ? (body as { data: unknown }).data
      : null
  if (!Array.isArray(list)) {
    throw new ApiError('protocol', '')
  }
  const ids: string[] = []
  for (const item of list) {
    const id = readModelId(item)
    if (id && !ids.includes(id)) {
      ids.push(id)
    }
  }
  return ids
}

/**
 * GET `/models`：先尝试 LLM Base URL + /models（OpenAI 兼容网关），
 * 路径不存在或返回非 JSON 时回退到 LLM 地址同源的 /models（DeepSeek 官方）。
 * 鉴权、限流、服务端错误与网络错误直接返回，不再切换地址。
 */
export const fetchModels = async (apiKey: string, signal?: AbortSignal): Promise<string[]> => {
  let lastError: unknown
  for (const url of getModelsUrls()) {
    try {
      return await readModelList(url, apiKey, signal)
    } catch (error) {
      lastError = error
      if (!(error instanceof ApiError)) {
        throw error
      }
      const pathMismatch =
        (error.kind === 'http' && error.status === 404) || error.kind === 'protocol'
      if (!pathMismatch) {
        throw error
      }
    }
  }
  throw lastError instanceof Error ? lastError : new ApiError('protocol', '')
}
