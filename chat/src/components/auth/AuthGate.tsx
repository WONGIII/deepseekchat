import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslate } from '../../i18n/index.js'
import { Button } from '../base/Button.js'
import { BrandMark } from '../base/BrandMark.js'
import { login, loadCurrentUser, register, useAuth } from '../../services/auth.js'
import { loadRemoteNetworkSettings } from '../../services/settings.js'
import { closeAuthOverlay, useAuthUi } from './authUiState.js'
import styles from './AuthGate.module.css'

/**
 * 本地优先外壳：默认直接挂载聊天应用，不需要登录。
 * 登录页作为可选覆盖层，从侧边栏或设置里的“登录 / 同步”打开。
 */
export const AuthGate = ({ children }: { children: ReactNode }) => {
  const t = useTranslate()
  const status = useAuth((state) => state.status)
  const authError = useAuth((state) => state.error)
  const sessionUsername = useAuth((state) => state.user?.username)
  const overlayOpen = useAuthUi((state) => state.open)
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [settingsReady, setSettingsReady] = useState(false)

  useEffect(() => {
    void loadCurrentUser()
  }, [])

  useEffect(() => {
    if (status !== 'authenticated' || !sessionUsername) {
      setSettingsReady(false)
      return
    }
    closeAuthOverlay()
    let cancelled = false
    void loadRemoteNetworkSettings(sessionUsername).finally(() => {
      if (!cancelled) {
        setSettingsReady(true)
      }
    })
    return () => {
      cancelled = true
    }
  }, [status, sessionUsername])

  if (status === 'loading') {
    return (
      <div className={styles.splash} role="status" aria-live="polite">
        <span className={styles.spinner} />
        {t('authLoading')}
      </div>
    )
  }

  if (status === 'authenticated' && !settingsReady) {
    return (
      <div className={styles.splash} role="status" aria-live="polite">
        <span className={styles.spinner} />
        {t('authLoading')}
      </div>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setFormError(null)
    try {
      await (mode === 'login' ? login : register)(username.trim(), password)
      // 本地会话与云端会话使用不同数据源；登录成功后重新初始化。
      window.location.reload()
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : String(submitError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {children}
      {overlayOpen && status !== 'authenticated' ? (
        <div
          className={styles.overlay}
          role="dialog"
          aria-modal="true"
          aria-label={t('authLoginTitle')}
        >
          <button
            type="button"
            className={styles.close}
            aria-label={t('cancel')}
            onClick={closeAuthOverlay}
          >
            ×
          </button>
          <div className={styles.panel}>
            <div className={styles.logoRow}>
              <BrandMark size={34} />
              <span className={styles.logoText}>Chat</span>
            </div>
            <h1 className={styles.title}>{t(mode === 'login' ? 'authLoginTitle' : 'authRegisterTitle')}</h1>
            <p className={styles.subtitle}>
              {mode === 'login' ? t('authLoginHint') : t('authRegisterHint')}
            </p>
            <form className={styles.form} onSubmit={submit}>
              <input
                className={styles.input}
                name="username"
                autoComplete="username"
                placeholder={t('authUsername')}
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                minLength={2}
                maxLength={32}
                required
              />
              <input
                className={styles.input}
                name="password"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                placeholder={t('authPassword')}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={6}
                maxLength={128}
                required
              />
              {formError ? (
                <div className={styles.error} role="alert">
                  {formError}
                </div>
              ) : status === 'error' && authError ? (
                <div className={styles.error} role="alert">
                  {authError}
                </div>
              ) : null}
              <Button type="submit" variant="primary" size="m" className={styles.submit} disabled={busy}>
                {t(mode === 'login' ? 'authLoginAction' : 'authRegisterAction')}
              </Button>
            </form>
            <div className={styles.links}>
              <button
                type="button"
                className={styles.link}
                onClick={() => {
                  setMode(mode === 'login' ? 'register' : 'login')
                  setFormError(null)
                }}
              >
                {t(mode === 'login' ? 'authSwitchToRegister' : 'authSwitchToLogin')}
              </button>
              <button type="button" className={styles.link} onClick={closeAuthOverlay}>
                {t('authLocalMode')}
              </button>
            </div>
            <p className={styles.terms}>{t('authAgreement')}</p>
          </div>
        </div>
      ) : null}
    </>
  )
}
