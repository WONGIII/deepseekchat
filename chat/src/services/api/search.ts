import { ApiError, request } from './client.js'
import { getSearchBaseUrl } from './endpoints.js'

/** 单次搜索返回条数（服务端默认 10，上限 50） */
export const SEARCH_RESULT_COUNT = 10

/** 归一化后的网页搜索结果：summary 交给模型，snippet 用于来源卡片展示。 */
export type SearchResult = {
  title: string
  url: string
  snippet: string
  summary: string
  siteName: string | null
  siteIcon: string | null
  /** UTC RFC 3339 发布时间，可能为 null */
  date: string | null
}

const isHttpUrl = (value: string) => {
  try {
    return ['http:', 'https:'].includes(new URL(value).protocol)
  } catch {
    return false
  }
}

const optionalString = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value.trim() : null

const text = (value: unknown) => (typeof value === 'string' ? value.trim() : '')

const readResult = (value: unknown): SearchResult | null => {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  const item = value as Record<string, unknown>
  const url = text(item.url)
  if (!url || !isHttpUrl(url)) {
    return null
  }
  return {
    title: text(item.title) || url,
    url,
    snippet: text(item.snippet),
    summary: text(item.summary),
    siteName: optionalString(item.site_name),
    siteIcon: optionalString(item.site_icon),
    date: optionalString(item.date),
  }
}

/**
 * POST {搜索前缀}/search：独立的网页搜索接口（Bearer 鉴权）。
 * 成功返回按 URL 去重后的结果；响应不符合协议时抛 ApiError('protocol')。
 */
export const searchWeb = async (
  apiKey: string,
  query: string,
  signal?: AbortSignal
): Promise<SearchResult[]> => {
  const response = await request(`${getSearchBaseUrl()}/search`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, count: SEARCH_RESULT_COUNT, ori_question: query }),
    signal,
  })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new ApiError('protocol', '')
  }
  if (typeof body !== 'object' || body === null || !('results' in body)) {
    throw new ApiError('protocol', '')
  }
  const results = (body as { results: unknown }).results
  if (!Array.isArray(results)) {
    throw new ApiError('protocol', '')
  }
  const byUrl = new Map<string, SearchResult>()
  for (const item of results) {
    const result = readResult(item)
    if (result && !byUrl.has(result.url)) {
      byUrl.set(result.url, result)
    }
  }
  return [...byUrl.values()]
}
