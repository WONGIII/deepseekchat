import { create } from 'zustand'

type HtmlPreviewState = {
  open: boolean
  html: string
  title: string
}

export const useHtmlPreview = create<HtmlPreviewState>(() => ({
  open: false,
  html: '',
  title: '',
}))

/** 从 HTML 里取标题：优先 <title>，其次第一个 <h1> */
export const extractHtmlTitle = (html: string) => {
  if (typeof DOMParser !== 'undefined') {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    const title = doc.title?.trim() || doc.querySelector('h1')?.textContent?.trim()
    if (title) {
      return title
    }
  }
  const fallback = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]
  return fallback?.replace(/<[^>]*>/g, '').trim() ?? ''
}

export const openHtmlPreview = (html: string, title: string) => {
  useHtmlPreview.setState({ open: true, html, title })
}

export const closeHtmlPreview = () => {
  useHtmlPreview.setState({ open: false })
}
