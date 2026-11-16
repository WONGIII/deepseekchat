import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { type Session, type SessionSummary } from './types.js'

/** IndexedDB 存储结构版本；新增或调整存储、索引时递增。 */
const DB_VERSION = 11

/** 文件原件按附件的本地 id 存储，所有引用同一 id 的消息共用一份；用于缩略图、打开查看，以及没有 serverId 时补传。 */
export type StoredFile = {
  id: string
  blob: Blob
}

/** 文件引用留在摘要中，删除会话时通过索引判断原件是否仍被引用。 */
type StoredSessionSummary = SessionSummary & { fileIds: string[] }

interface ChatDB extends DBSchema {
  /** 当前使用的会话，界面读写都在这里 */
  sessions: { key: string; value: Session }
  sessionSummaries: {
    key: string
    value: StoredSessionSummary
    indexes: { fileIds: string }
  }
  /** 当前使用的文件原件 */
  files: { key: string; value: StoredFile }
  /**
   * 历史导入的暂存：导入过程中会话与原件只写入这两个存储，`sessions` / `files` 保持不变；
   * 全部校验通过后在一个事务中替换正式数据并清空暂存，失败时只清空暂存。
   */
  sessionsStaging: { key: string; value: Session }
  filesStaging: { key: string; value: StoredFile }
}

let dbPromise: Promise<IDBPDatabase<ChatDB>> | null = null

const abortTransaction = (tx: { abort(): void }) => {
  try {
    tx.abort()
  } catch {
    // 事务已因请求失败中止。
  }
}

// 每个操作拿到连接后立即创建事务；IndexedDB 按创建顺序执行范围重叠的事务，读取总能看到之前发起的写入。
const getDB = () =>
  (dbPromise ??= openDB<ChatDB>('chat-demo', DB_VERSION, {
    upgrade(db, _oldVersion, _newVersion, tx) {
      // 初始化当前格式需要的存储与索引，不迁移旧消息。
      for (const name of ['sessions', 'files', 'sessionsStaging', 'filesStaging'] as const) {
        if (!db.objectStoreNames.contains(name)) {
          db.createObjectStore(name, { keyPath: 'id' })
        }
      }
      const summaries = db.objectStoreNames.contains('sessionSummaries')
        ? tx.objectStore('sessionSummaries')
        : db.createObjectStore('sessionSummaries', { keyPath: 'id' })
      if (!summaries.indexNames.contains('fileIds')) {
        summaries.createIndex('fileIds', 'fileIds', { multiEntry: true })
      }
    },
  }).catch((error: unknown) => {
    // 打开失败后允许界面重试，避免一直复用已拒绝的 Promise。
    dbPromise = null
    throw error
  }))

/**
 * 会话内容保存在服务器，按登录用户隔离；附件原件、草稿和历史导入暂存仍在本机
 * IndexedDB。下面五个函数是 store.ts 使用的会话持久化入口。
 */
const sessionRequest = async (path: string, init?: RequestInit): Promise<Response> => {
  const response = await fetch(`/api/sessions${path}`, {
    credentials: 'same-origin',
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    let message = `会话服务返回 HTTP ${response.status}`
    try {
      const body = (await response.json()) as { error?: unknown }
      if (typeof body.error === 'string') {
        message = body.error
      }
    } catch {
      // 保留状态码文本
    }
    throw new Error(message)
  }
  return response
}

export const loadSessionSummaries = async (): Promise<SessionSummary[]> => {
  const response = await sessionRequest('')
  return (await response.json()) as SessionSummary[]
}

export const loadSession = async (id: string): Promise<Session | undefined> => {
  const response = await sessionRequest(`/${encodeURIComponent(id)}`)
  return (await response.json()) as Session
}

const saveQueues = new Map<string, Promise<void>>()

export const saveSession = (session: Session, files: StoredFile[] = []): Promise<void> => {
  const id = session.id
  const previous = saveQueues.get(id) ?? Promise.resolve()
  const next = previous
    .catch(() => undefined)
    .then(async () => {
      if (files.length > 0) {
        await saveFiles(files)
      }
      await sessionRequest(`/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(session),
      })
    })
  saveQueues.set(id, next)
  const clear = () => {
    if (saveQueues.get(id) === next) {
      saveQueues.delete(id)
    }
  }
  next.then(clear, clear)
  return next
}

/** 未打开的会话也可重命名，服务器只更新标题。 */
export const renameSession = async (id: string, title: string): Promise<void> => {
  await sessionRequest(`/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  })
}

const deleteLocalFiles = async (ids: string[]): Promise<void> => {
  if (ids.length === 0) {
    return
  }
  const tx = (await getDB()).transaction('files', 'readwrite')
  await Promise.all([...ids.map((id) => tx.store.delete(id)), tx.done])
}

/** 删除服务器会话，并清理该会话独占的本地附件原件。 */
export const deleteSession = async (sessionId: string): Promise<void> => {
  await saveQueues.get(sessionId)?.catch(() => undefined)
  const session = await loadSession(sessionId).catch(() => undefined)
  await sessionRequest(`/${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
  saveQueues.delete(sessionId)
  const fileIds = session
    ? [
        ...new Set(
          session.messages.flatMap((message) =>
            message.role === 'user' ? message.files.map((file) => file.id) : []
          )
        ),
      ]
    : []
  await deleteLocalFiles(fileIds)
}

/** 导入/清空历史时整体替换服务器会话。 */
export const replaceAllSessions = async (sessions: Session[]): Promise<SessionSummary[]> => {
  await Promise.all([...saveQueues.values()].map((promise) => promise.catch(() => undefined)))
  saveQueues.clear()
  const response = await sessionRequest('', {
    method: 'PUT',
    body: JSON.stringify({ sessions }),
  })
  return (await response.json()) as SessionSummary[]
}

const putFiles = async (store: 'files' | 'filesStaging', files: StoredFile[]) => {
  const tx = (await getDB()).transaction(store, 'readwrite')
  await Promise.all([...files.map((file) => tx.store.put(file)), tx.done])
}

export const saveFiles = (files: StoredFile[]): Promise<void> => putFiles('files', files)

export const loadFile = async (id: string): Promise<Blob | undefined> =>
  (await (await getDB()).get('files', id))?.blob

/** 清空导入暂存：开始导入前清除上次中断（如关闭页面）遗留的数据，导入失败后清除本次写入。 */
export const clearStaging = async (): Promise<void> => {
  const tx = (await getDB()).transaction(['sessionsStaging', 'filesStaging'], 'readwrite')
  await Promise.all([
    tx.objectStore('sessionsStaging').clear(),
    tx.objectStore('filesStaging').clear(),
    tx.done,
  ])
}

export const addStagingSession = async (session: Session): Promise<void> => {
  await (await getDB()).add('sessionsStaging', session)
}

export const saveStagingFiles = (files: StoredFile[]): Promise<void> =>
  putFiles('filesStaging', files)

/** 图片位于会话 JSON 之后；全部读完后回填具体 MIME，保留归档给出的 isImage 标识。 */
export const updateStagingFileTypes = async (types: ReadonlyMap<string, string>): Promise<void> => {
  const tx = (await getDB()).transaction('sessionsStaging', 'readwrite')
  await Promise.all([
    (async () => {
      for (let cursor = await tx.store.openCursor(); cursor; cursor = await cursor.continue()) {
        const session = cursor.value
        let changed = false
        for (const message of session.messages) {
          if (message.role !== 'user') {
            continue
          }
          for (const file of message.files) {
            const type = types.get(file.id) ?? ''
            if (file.mimeType !== type) {
              file.mimeType = type
              changed = true
            }
          }
        }
        if (changed) {
          await cursor.update(session)
        }
      }
    })(),
    tx.done,
  ])
}

/**
 * 导入完成后把暂存会话整体推到服务器，并把暂存附件原件移入本地 files。
 * 服务器写入失败时暂存保持原样，界面可以重试；本机 sessions 旧缓存一并清理。
 */
export const commitStaging = async (): Promise<SessionSummary[]> => {
  const staged: Session[] = []
  const readTx = (await getDB()).transaction('sessionsStaging', 'readonly')
  for await (const cursor of readTx.store) {
    staged.push(cursor.value)
  }
  await readTx.done

  const summaries = await replaceAllSessions(staged)

  const tx = (await getDB()).transaction(
    ['sessions', 'sessionSummaries', 'files', 'sessionsStaging', 'filesStaging'],
    'readwrite'
  )
  await Promise.all([
    (async () => {
      try {
        await tx.objectStore('sessions').clear()
        await tx.objectStore('sessionSummaries').clear()
        const files = tx.objectStore('files')
        await files.clear()
        for await (const cursor of tx.objectStore('filesStaging')) {
          await files.put(cursor.value)
        }
        await tx.objectStore('sessionsStaging').clear()
        await tx.objectStore('filesStaging').clear()
      } catch (error) {
        abortTransaction(tx)
        throw error
      }
    })(),
    tx.done,
  ])
  return summaries
}

/** 本地存储失败不打断对话，只在控制台留痕 */
export const reportStorageError = (action: string) => (error: unknown) => {
  // eslint-disable-next-line no-console
  console.error(`[storage] ${action} failed`, error)
}
