import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslate } from '../../i18n/index.js'
import { Button } from '../base/Button.js'
import { login, loadCurrentUser, register, useAuth } from '../../services/auth.js'
import styles from './AuthGate.module.css'

/** 登录/注册外壳；认证成功后才挂载聊天应用，避免未登录时发起会话请求 */
export const AuthGate = ({ children }: { children: ReactNode }) => {
  const t = useTranslate()
  const status = useAuth((state) => state.status)
  const error = useAuth((state) => state.error)
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    void loadCurrentUser()
  }, [])

  if (status === 'authenticated') {
    return children
  }

  if (status === 'loading') {
    return (
      <div className={styles.splash} role="status" aria-live="polite">
        <span className={styles.spinner} />
        {t('authLoading')}
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>{t('authServerError')}</h1>
          <p className={styles.hint}>{error}</p>
          <Button variant="primary" size="m" onClick={() => void loadCurrentUser()}>
            {t('retry')}
          </Button>
        </div>
      </div>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setFormError(null)
    try {
      await (mode === 'login' ? login : register)(username.trim(), password)
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : String(submitError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.page}>
      <form className={styles.card} onSubmit={submit}>
        <div className={styles.brand}>deepseek</div>
        <h1 className={styles.title}>
          {t(mode === 'login' ? 'authLoginTitle' : 'authRegisterTitle')}
        </h1>
        <p className={styles.hint}>
          {t(mode === 'login' ? 'authLoginHint' : 'authRegisterHint')}
        </p>
        <label className={styles.field}>
          <span>{t('authUsername')}</span>
          <input
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            minLength={2}
            maxLength={32}
            required
            autoFocus
          />
        </label>
        <label className={styles.field}>
          <span>{t('authPassword')}</span>
          <input
            name="password"
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            minLength={6}
            maxLength={128}
            required
          />
        </label>
        {formError ? (
          <div className={styles.error} role="alert">
            {formError}
          </div>
        ) : null}
        <Button
          type="submit"
          variant="primary"
          size="m"
          className={styles.submit}
          disabled={busy}
        >
          {t(mode === 'login' ? 'authLoginAction' : 'authRegisterAction')}
        </Button>
        <button
          type="button"
          className={styles.switch}
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login')
            setFormError(null)
          }}
        >
          {t(mode === 'login' ? 'authSwitchToRegister' : 'authSwitchToLogin')}
        </button>
      </form>
    </div>
  )
}
