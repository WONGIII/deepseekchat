package com.wongiii.chat.data.repo

import com.wongiii.chat.data.local.LocalSessionStore
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.remote.SessionApi

interface SessionRepository {
    suspend fun list(): List<SessionSummary>
    suspend fun load(id: String): ChatSession?
    suspend fun save(session: ChatSession)
    suspend fun rename(id: String, title: String)
    suspend fun delete(id: String)
    suspend fun replaceAll(sessions: List<ChatSession>)
    suspend fun search(query: String): List<SessionSummary>
    suspend fun count(): Int
    suspend fun exportAll(): List<ChatSession>
}

class LocalRepository(private val store: LocalSessionStore) : SessionRepository {
    override suspend fun list() = store.list()
    override suspend fun load(id: String) = store.load(id)
    override suspend fun save(session: ChatSession) = store.save(session)
    override suspend fun rename(id: String, title: String) {
        store.rename(id, title)
    }
    override suspend fun delete(id: String) = store.delete(id)
    override suspend fun replaceAll(sessions: List<ChatSession>) = store.replaceAll(sessions)
    override suspend fun search(query: String) = store.search(query)
    override suspend fun count() = store.count()
    override suspend fun exportAll() = store.exportAll()
}

class CloudRepository(
    private val api: SessionApi,
    private val cache: LocalSessionStore,
) : SessionRepository {
    override suspend fun list(): List<SessionSummary> {
        val remote = api.list()
        remote.forEach { summary ->
            runCatching { cache.load(summary.id) }
        }
        return remote
    }

    override suspend fun load(id: String): ChatSession? {
        val remote = api.load(id)
        if (remote != null) cache.save(remote)
        return remote ?: cache.load(id)
    }

    override suspend fun save(session: ChatSession) {
        api.save(session)
        cache.save(session)
    }

    override suspend fun rename(id: String, title: String) {
        val current = load(id)
        if (current != null) {
            val updated = current.copy(title = title.take(120), updatedAt = System.currentTimeMillis())
            api.save(updated)
            cache.save(updated)
        } else {
            api.rename(id, title)
        }
    }

    override suspend fun delete(id: String) {
        api.delete(id)
    }

    override suspend fun replaceAll(sessions: List<ChatSession>) {
        api.replaceAll(sessions)
        cache.replaceAll(sessions)
    }

    override suspend fun search(query: String): List<SessionSummary> {
        val remote = runCatching { api.search(query) }.getOrNull()
        if (remote != null) return remote
        return cache.search(query)
    }

    override suspend fun count(): Int = list().size

    override suspend fun exportAll(): List<ChatSession> {
        val summaries = api.list()
        return summaries.mapNotNull { api.load(it.id) }
    }
}
