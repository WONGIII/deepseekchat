import { useTranslate } from '../../i18n/index.js'
import { memo, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import Prism from 'prismjs'
import {
  getHighlightLang,
  isHighlightLoaded,
  loadHighlight,
} from '../../services/markdown/highlight.js'
import {
  IconCheckOutline16,
  IconCopyOutline16,
  IconDownloadOutline16,
  IconPlayCircleOutline16,
} from '../base/icons.js'
import { showToast } from '../base/toastState.js'
import { copyText } from '../../utils/clipboard.js'
import { MermaidDiagram } from './MermaidDiagram.js'
import { extractHtmlTitle, openHtmlPreview } from './htmlPreviewState.js'
import styles from './CodeBlock.module.css'

/** 代码块标题栏：左侧语言名，右侧复制 / 下载 / 运行 */
const CodeInfoRow = ({
  code,
  infostring,
  loading,
  showDiagram,
  onToggleDiagram,
}: {
  code: string
  infostring: string
  loading: boolean
  showDiagram: boolean
  onToggleDiagram?: () => void
}) => {
  const t = useTranslate()
  const [copied, setCopied] = useState(false)
  const isHtml = infostring.trim().split(/\s+/)[0].toLowerCase() === 'html'

  const downloadHtml = () => {
    const title = extractHtmlTitle(code) || 'preview'
    const filename = `${title.replace(/[\\/:*?"<>|]+/g, '_').slice(0, 80)}.html`
    const url = URL.createObjectURL(new Blob([code], { type: 'text/html;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  return (
    <div className={styles.container}>
      {onToggleDiagram ? (
        <div className="md-code-block-switch" aria-label={t('mermaidSwitchLabel')}>
          <button
            type="button"
            className={styles.button}
            aria-pressed={showDiagram}
            onClick={() => !showDiagram && onToggleDiagram()}
          >
            {t('mermaidDiagram')}
          </button>
          <button
            type="button"
            className={styles.button}
            aria-pressed={!showDiagram}
            onClick={() => showDiagram && onToggleDiagram()}
          >
            {t('mermaidCode')}
          </button>
        </div>
      ) : (
        <span className={styles.name}>{infostring}</span>
      )}
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.button}
          onClick={async () => {
            if (!(await copyText(code))) {
              showToast({ key: 'copyFailed' })
              return
            }
            setCopied(true)
            setTimeout(() => setCopied(false), 1500)
          }}
        >
          {copied ? <IconCheckOutline16 /> : <IconCopyOutline16 />}
          <span>{t(copied ? 'copied' : 'copy')}</span>
        </button>
        {isHtml ? (
          <>
            <button type="button" className={styles.button} disabled={loading} onClick={downloadHtml}>
              <IconDownloadOutline16 />
              <span>{t('download')}</span>
            </button>
            <button
              type="button"
              className={styles.button}
              disabled={loading}
              onClick={() => openHtmlPreview(code, extractHtmlTitle(code) || t('htmlPreview'))}
            >
              <IconPlayCircleOutline16 />
              <span>{t('run')}</span>
            </button>
          </>
        ) : null}
      </div>
    </div>
  )
}

/** 流式代码先用纯文本渲染，只让新增字符淡入；结束后再切回高亮 HTML */
const StreamingCode = memo(function StreamingCode({ code }: { code: string }) {
  const chars = useMemo(() => Array.from(code), [code])
  const previousLength = useRef(0)
  const freshStart = Math.min(previousLength.current, chars.length)
  previousLength.current = chars.length
  return (
    <pre>
      {chars.map((char, index) => {
        if (index >= freshStart) {
          const delay = Math.min((index - freshStart) * 8, 96)
          return (
            <span
              key={index}
              className="ds-streaming-char"
              style={{ animationDelay: `${delay}ms` }}
            >
              {char}
            </span>
          )
        }
        return char
      })}
    </pre>
  )
})

/** 代码块：语言包按需加载，不支持或尚未加载的语言按纯文本显示 */
export const CodeBlock = memo(function CodeBlock({
  code,
  infostring,
  loading = false,
}: {
  code: string
  infostring: string
  loading?: boolean
}) {
  const isMermaid = infostring.toLowerCase() === 'mermaid'
  const [showDiagram, setShowDiagram] = useState(true)
  // 语言包注册在全局 Prism 上，每次渲染直接判断；加载完成后触发一次重渲染
  const rerender = useReducer((n: number) => n + 1, 0)[1]
  const lang = getHighlightLang(infostring)
  const loaded = lang !== null && isHighlightLoaded(lang)
  useEffect(() => {
    if (lang === null || loaded) {
      return
    }
    let unmounted = false
    loadHighlight(lang).then(() => {
      if (!unmounted) {
        rerender()
      }
    })
    return () => {
      unmounted = true
    }
  }, [lang, loaded, rerender])
  const renderedHtml = useMemo(() => {
    const name = lang !== null && loaded ? lang : 'plaintext'
    return Prism.highlight(code, Prism.languages[name], name)
  }, [code, lang, loaded])
  return (
    <div className="md-code-block">
      <div className="md-code-block-banner">
        <CodeInfoRow
          code={code}
          infostring={infostring}
          loading={loading}
          showDiagram={showDiagram}
          onToggleDiagram={isMermaid ? () => setShowDiagram((show) => !show) : undefined}
        />
      </div>
      {isMermaid && showDiagram ? (
        <MermaidDiagram code={code} loading={loading} />
      ) : loading ? (
        <StreamingCode code={code} />
      ) : (
        <pre dangerouslySetInnerHTML={{ __html: renderedHtml }}></pre>
      )}
    </div>
  )
})
