package controllers

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.json.Json

class ForwardingService(private val json: Json) {
    private val mutex: Mutex = Mutex()
    private val sessions: MutableMap<DefaultWebSocketServerSession, ConnectionsManager> = mutableMapOf()

    private val http = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(
        url: String,
        server: DefaultWebSocketServerSession,
    ) {
        val manager = mutex.withLock {
            sessions.computeIfAbsent(server) {
                ConnectionsManager(json, server, http)
            }
        }
        manager.connectOrThrow(url)
    }

    suspend fun closeAllConnections(server: DefaultWebSocketServerSession) {
        mutex.withLock {
            val manager = sessions[server] ?: return
            manager.close()
            sessions.remove(server)
        }
    }

    suspend fun forward(
        url: String,
        request: String,
        server: DefaultWebSocketServerSession
    ) {
        val manager = mutex.withLock {
            sessions.computeIfAbsent(server) {
                ConnectionsManager(json, server, http)
            }
        }
        manager.forwardOrThrow(url, request)
    }
}

