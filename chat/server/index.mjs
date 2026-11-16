import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import express from 'express'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const DATA_DIR = process.env.DATA_DIR || path.join(ROOT, 'data')
const USERS_FILE = path.join(DATA_DIR, 'users.json')
const TOKENS_FILE = path.join(DATA_DIR, 'tokens.json')
const SESSION_DIR = path.join(DATA_DIR, 'sessions')
const SETTINGS_DIR = path.join(DATA_DIR, 'settings')
const SETTINGS_KEY_FILE = path.join(DATA_DIR, 'settings.key')
const DIST_DIR = path.resolve(ROOT, '..', 'dist')

const HOST = process.env.HOST || '0.0.0.0'
const PORT = Number(process.env.PORT || 8098)
const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000
const COOKIE_NAME = 'ds_session'
const USERNAME_RE = /^[\p{L}\p{N}_-]{2,32}$/u

await fs.mkdir(DATA_DIR, { recursive: true })
await fs.mkdir(SESSION_DIR, { recursive: true })
await fs.mkdir(SETTINGS_DIR, { recursive: true })

const settingsKey = process.env.SETTINGS_SECRET
  ? crypto.createHash('sha256').update(process.env.SETTINGS_SECRET).digest()
  : await (async () => {
      try {
        const existing = Buffer.from((await fs.readFile(SETTINGS_KEY_FILE, 'utf8')).trim(), 'hex')
        if (existing.length === 32) return existing
      } catch (error) {
        if (error.code !== 'ENOENT') throw error
      }
      const generated = crypto.randomBytes(32)
      await fs.writeFile(SETTINGS_KEY_FILE, generated.toString('hex'), { mode: 0o600 })
      return generated
    })()

const readJson = async (file, fallback) => {
  try {
    return JSON.parse(await fs.readFile(file, 'utf8'))
  } catch (error) {
    if (error.code === 'ENOENT') return fallback
    throw error
  }
}

const writeJson = async (file, value) => {
  await fs.mkdir(path.dirname(file), { recursive: true })
  const temp = `${file}.${process.pid}.${crypto.randomBytes(4).toString('hex')}.tmp`
  await fs.writeFile(temp, JSON.stringify(value), 'utf8')
  await fs.rename(temp, file)
}

const users = await readJson(USERS_FILE, {})
const tokens = await readJson(TOKENS_FILE, {})

const saveUsers = () => writeJson(USERS_FILE, users)
const saveTokens = () => writeJson(TOKENS_FILE, tokens)

const findUserByName = (name) =>
  Object.values(users).find((user) => user.username.toLowerCase() === name.toLowerCase())

const hashPassword = (password, salt) =>
  crypto.scryptSync(password, salt, 64).toString('hex')

const parseCookies = (header = '') =>
  header.split(';').reduce((result, item) => {
    const index = item.indexOf('=')
    if (index > 0) {
      result[item.slice(0, index).trim()] = decodeURIComponent(item.slice(index + 1).trim())
    }
    return result
  }, {})

const setSessionCookie = (req, res, token) => {
  const secure = req.headers['x-forwarded-proto'] === 'https' || req.secure
  res.cookie(COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure,
    maxAge: TOKEN_TTL_MS,
    path: '/',
  })
}

const clearSessionCookie = (req, res) => {
  const secure = req.headers['x-forwarded-proto'] === 'https' || req.secure
  res.clearCookie(COOKIE_NAME, { httpOnly: true, sameSite: 'lax', secure, path: '/' })
}

const createToken = async (userId) => {
  const token = crypto.randomBytes(32).toString('hex')
  tokens[token] = { userId, expiresAt: Date.now() + TOKEN_TTL_MS }
  await saveTokens()
  return token
}

const currentUser = async (req) => {
  const token = parseCookies(req.headers.cookie)[COOKIE_NAME]
  if (!token) return null
  const entry = tokens[token]
  if (!entry || entry.expiresAt <= Date.now() || !users[entry.userId]) {
    if (entry) {
      delete tokens[token]
      await saveTokens()
    }
    return null
  }
  return users[entry.userId]
}

const requireAuth = async (req, res, next) => {
  try {
    const user = await currentUser(req)
    if (!user) {
      res.status(401).json({ error: '未登录' })
      return
    }
    req.user = user
    next()
  } catch (error) {
    next(error)
  }
}

const sessionsDirFor = (userId) => path.join(SESSION_DIR, userId)
const sessionFile = (userId, id) => path.join(sessionsDirFor(userId), `${id}.json`)

const validSessionId = (id) => typeof id === 'string' && /^[A-Za-z0-9_-]{8,80}$/.test(id)

const toSummary = (session) => ({
  id: session.id,
  title: typeof session.title === 'string' ? session.title : '',
  createdAt: Number(session.createdAt) || 0,
  updatedAt: Number(session.updatedAt) || 0,
})

const readSession = async (userId, id) => {
  if (!validSessionId(id)) return null
  try {
    const session = JSON.parse(await fs.readFile(sessionFile(userId, id), 'utf8'))
    return session && session.id === id ? session : null
  } catch (error) {
    if (error.code === 'ENOENT') return null
    throw error
  }
}

const listSessions = async (userId) => {
  const dir = sessionsDirFor(userId)
  let files = []
  try {
    files = await fs.readdir(dir)
  } catch (error) {
    if (error.code === 'ENOENT') return []
    throw error
  }
  const summaries = []
  for (const file of files) {
    if (!file.endsWith('.json')) continue
    const session = await readSession(userId, file.slice(0, -5))
    if (session) summaries.push(toSummary(session))
  }
  return summaries.sort((a, b) => b.updatedAt - a.updatedAt)
}

const validateSession = (session, user) => {
  if (!session || typeof session !== 'object' || !validSessionId(session.id)) return null
  if (typeof session.title !== 'string') return null
  if (!Array.isArray(session.messages)) return null
  // The client owns the message schema; the server only enforces identity/size boundaries.
  return {
    ...session,
    title: session.title.slice(0, 120),
    createdAt: Number(session.createdAt) || Date.now(),
    updatedAt: Number(session.updatedAt) || Date.now(),
  }
}

const settingsFile = (userId) => path.join(SETTINGS_DIR, `${userId}.json`)

const encryptSettings = (payload) => {
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', settingsKey, iv)
  const data = Buffer.concat([
    cipher.update(JSON.stringify(payload), 'utf8'),
    cipher.final(),
  ])
  return {
    iv: iv.toString('hex'),
    tag: cipher.getAuthTag().toString('hex'),
    data: data.toString('base64'),
  }
}

const decryptSettings = (record) => {
  const decipher = crypto.createDecipheriv(
    'aes-256-gcm',
    settingsKey,
    Buffer.from(record.iv, 'hex')
  )
  decipher.setAuthTag(Buffer.from(record.tag, 'hex'))
  const plaintext = Buffer.concat([
    decipher.update(Buffer.from(record.data, 'base64')),
    decipher.final(),
  ])
  return JSON.parse(plaintext.toString('utf8'))
}

const NETWORK_FIELDS = [
  'llmBaseUrl',
  'searchBaseUrl',
  'speechBaseUrl',
  'llmApiKey',
  'searchApiKey',
  'speechApiKey',
]

const validateNetworkSettings = (raw) => {
  if (!raw || typeof raw !== 'object') return null
  const result = {}
  for (const field of NETWORK_FIELDS) {
    const value = raw[field]
    if (value === null || value === undefined || value === '') {
      result[field] = null
      continue
    }
    if (typeof value !== 'string') return null
    const limit = field.endsWith('Url') ? 2048 : 4096
    if (value.length > limit) return null
    result[field] = value
  }
  return result
}


const app = express()
app.disable('x-powered-by')
app.set('trust proxy', 1)
app.use(express.json({ limit: '24mb' }))

app.get('/api/health', (_req, res) => res.json({ ok: true }))

app.post('/api/auth/register', async (req, res, next) => {
  try {
    const username = String(req.body?.username || '').trim()
    const password = String(req.body?.password || '')
    if (!USERNAME_RE.test(username)) {
      res.status(400).json({ error: '用户名需为 2-32 位字母、数字、下划线、短横线或中文' })
      return
    }
    if (password.length < 6 || password.length > 128) {
      res.status(400).json({ error: '密码需为 6-128 位' })
      return
    }
    if (findUserByName(username)) {
      res.status(409).json({ error: '用户名已存在' })
      return
    }
    const salt = crypto.randomBytes(16).toString('hex')
    const user = {
      id: crypto.randomUUID(),
      username,
      salt,
      hash: hashPassword(password, salt),
      createdAt: Date.now(),
    }
    users[user.id] = user
    await saveUsers()
    const token = await createToken(user.id)
    setSessionCookie(req, res, token)
    res.status(201).json({ username: user.username })
  } catch (error) {
    next(error)
  }
})

app.post('/api/auth/login', async (req, res, next) => {
  try {
    const username = String(req.body?.username || '').trim()
    const password = String(req.body?.password || '')
    const user = findUserByName(username)
    if (!user || password.length > 128) {
      res.status(401).json({ error: '用户名或密码错误' })
      return
    }
    const expected = Buffer.from(user.hash, 'hex')
    const actual = Buffer.from(hashPassword(password, user.salt), 'hex')
    if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
      res.status(401).json({ error: '用户名或密码错误' })
      return
    }
    const token = await createToken(user.id)
    setSessionCookie(req, res, token)
    res.json({ username: user.username })
  } catch (error) {
    next(error)
  }
})

app.get('/api/auth/me', async (req, res, next) => {
  try {
    const user = await currentUser(req)
    if (!user) {
      res.status(401).json({ error: '未登录' })
      return
    }
    res.json({ username: user.username })
  } catch (error) {
    next(error)
  }
})

app.post('/api/auth/logout', async (req, res, next) => {
  try {
    const token = parseCookies(req.headers.cookie)[COOKIE_NAME]
    if (token && tokens[token]) {
      delete tokens[token]
      await saveTokens()
    }
    clearSessionCookie(req, res)
    res.json({ ok: true })
  } catch (error) {
    next(error)
  }
})

app.get('/api/settings/network', requireAuth, async (req, res, next) => {
  try {
    try {
      const record = JSON.parse(await fs.readFile(settingsFile(req.user.id), 'utf8'))
      res.json({ settings: decryptSettings(record) })
    } catch (error) {
      if (error.code === 'ENOENT') {
        res.json({ settings: null })
        return
      }
      throw error
    }
  } catch (error) {
    next(error)
  }
})

app.put('/api/settings/network', requireAuth, async (req, res, next) => {
  try {
    const settings = validateNetworkSettings(req.body?.settings)
    if (!settings) {
      res.status(400).json({ error: '无效的网络设置' })
      return
    }
    await writeJson(settingsFile(req.user.id), encryptSettings(settings))
    res.json({ settings })
  } catch (error) {
    next(error)
  }
})

app.get('/api/sessions', requireAuth, async (req, res, next) => {
  try {
    res.json(await listSessions(req.user.id))
  } catch (error) {
    next(error)
  }
})

app.get('/api/sessions/:id', requireAuth, async (req, res, next) => {
  try {
    const session = await readSession(req.user.id, req.params.id)
    if (!session) {
      res.status(404).json({ error: '会话不存在' })
      return
    }
    res.json(session)
  } catch (error) {
    next(error)
  }
})

app.put('/api/sessions/:id', requireAuth, async (req, res, next) => {
  try {
    const session = validateSession(req.body, req.user)
    if (!session || session.id !== req.params.id) {
      res.status(400).json({ error: '无效的会话数据' })
      return
    }
    await writeJson(sessionFile(req.user.id, session.id), session)
    res.json(toSummary(session))
  } catch (error) {
    next(error)
  }
})

app.patch('/api/sessions/:id', requireAuth, async (req, res, next) => {
  try {
    const session = await readSession(req.user.id, req.params.id)
    if (!session) {
      res.status(404).json({ error: '会话不存在' })
      return
    }
    session.title = String(req.body?.title || '').slice(0, 120)
    await writeJson(sessionFile(req.user.id, session.id), session)
    res.json(toSummary(session))
  } catch (error) {
    next(error)
  }
})

app.delete('/api/sessions/:id', requireAuth, async (req, res, next) => {
  try {
    if (validSessionId(req.params.id)) {
      await fs.unlink(sessionFile(req.user.id, req.params.id)).catch((error) => {
        if (error.code !== 'ENOENT') throw error
      })
    }
    res.json({ ok: true })
  } catch (error) {
    next(error)
  }
})

app.put('/api/sessions', requireAuth, async (req, res, next) => {
  try {
    const incoming = Array.isArray(req.body?.sessions) ? req.body.sessions : null
    if (!incoming) {
      res.status(400).json({ error: 'sessions must be an array' })
      return
    }
    const validated = []
    for (const raw of incoming) {
      const session = validateSession(raw, req.user)
      if (!session) {
        res.status(400).json({ error: '无效的会话数据' })
        return
      }
      validated.push(session)
    }
    const dir = sessionsDirFor(req.user.id)
    await fs.mkdir(dir, { recursive: true })
    const keep = new Set(validated.map((session) => `${session.id}.json`))
    const existing = await fs.readdir(dir).catch(() => [])
    for (const file of existing) {
      if (file.endsWith('.json') && !keep.has(file)) {
        await fs.unlink(path.join(dir, file))
      }
    }
    for (const session of validated) {
      await writeJson(sessionFile(req.user.id, session.id), session)
    }
    res.json(validated.map(toSummary).sort((a, b) => b.updatedAt - a.updatedAt))
  } catch (error) {
    next(error)
  }
})

app.use(express.static(DIST_DIR, { index: false, maxAge: '1h' }))
app.get('*', async (req, res, next) => {
  if (req.path.startsWith('/api/')) {
    res.status(404).json({ error: 'Not found' })
    return
  }
  try {
    await fs.access(path.join(DIST_DIR, 'index.html'))
    res.sendFile(path.join(DIST_DIR, 'index.html'))
  } catch {
    res.status(503).send('Frontend build missing. Run `pnpm build` in chat/.')
  }
})

app.use((error, _req, res, _next) => {
  console.error('[server]', error)
  res.status(500).json({ error: '服务器内部错误' })
})

app.listen(PORT, HOST, () => {
  console.log(`[server] listening on http://${HOST}:${PORT}`)
})
