import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'
import { useTranslate } from '../../i18n/index.js'
import { IconCloseOutline16 } from '../base/icons.js'
import { IconButton } from '../base/IconButton.js'
import { closeHtmlPreview, useHtmlPreview } from './htmlPreviewState.js'
import styles from './HtmlPreviewPanel.module.css'

/** 右侧 HTML 预览面板：作为布局的一部分挤压聊天区，关闭时收合 */
export const HtmlPreviewPanel = () => {
  const t = useTranslate()
  const open = useHtmlPreview((state) => state.open)
  const html = useHtmlPreview((state) => state.html)
  const title = useHtmlPreview((state) => state.title)
  const [mounted, setMounted] = useState(false)
  const [visible, setVisible] = useState(false)
  const panelRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (open) {
      setMounted(true)
      return
    }
    setVisible(false)
    const timer = setTimeout(() => setMounted(false), 340)
    return () => clearTimeout(timer)
  }, [open])

  // 先让浏览器真正绘制 width:0，再切到目标宽度；否则两个状态同帧合并，看起来就是瞬移。
  useEffect(() => {
    if (!open || !mounted) {
      return
    }
    const element = panelRef.current
    if (!element) {
      return
    }
    // 强制一次布局，确保起始宽度已经被采用
    void element.offsetWidth
    let innerFrame = 0
    const outerFrame = requestAnimationFrame(() => {
      innerFrame = requestAnimationFrame(() => setVisible(true))
    })
    return () => {
      cancelAnimationFrame(outerFrame)
      if (innerFrame) {
        cancelAnimationFrame(innerFrame)
      }
    }
  }, [mounted, open])

  useEffect(() => {
    if (!open) {
      return
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeHtmlPreview()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  if (!mounted) {
    return null
  }

  return (
    <aside
      ref={panelRef}
      className={clsx(styles.panel, visible && styles.panelOpen)}
      aria-hidden={!visible}
    >
      <header className={styles.header}>
        <div className={styles.title} role="heading">
          {title || t('htmlPreview')}
        </div>
        <IconButton
          icon={<IconCloseOutline16 />}
          aria-label={t('close')}
          onClick={closeHtmlPreview}
        />
      </header>
      <iframe
        className={styles.frame}
        title={title || t('htmlPreview')}
        srcDoc={html}
        sandbox="allow-scripts allow-forms allow-modals allow-popups"
        referrerPolicy="no-referrer"
      />
    </aside>
  )
}
