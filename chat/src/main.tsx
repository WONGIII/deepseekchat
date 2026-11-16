import { createRoot } from 'react-dom/client'
import { App } from './app/App.js'
import { AuthGate } from './components/auth/AuthGate.js'
import { setupTheme } from './app/theme.js'
import { ensureKatex } from './services/markdown/katex.js'
import './app/theme.css'
import './app/global.css'

ensureKatex()
setupTheme()

createRoot(document.getElementById('root') as HTMLElement).render(
  <AuthGate>
    <App />
  </AuthGate>
)

if (window.isSecureContext && 'serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').catch(() => {
    // PWA offline cache is optional; ignore registration failures.
  })
}
