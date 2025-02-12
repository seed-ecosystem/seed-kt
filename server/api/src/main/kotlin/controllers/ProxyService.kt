package controllers

import BaseEventResponseSerializable
import ConnectEventSerializable
import ResponseSerializable
import WebsocketResponseSerializable
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
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class ForwardingService(private val json: Json) {
    private val sessions: MutableMap<DefaultWebSocketServerSession, ConnectionsManager> = mutableMapOf()

    private val http = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    private val connections =
        ConcurrentHashMap<DefaultWebSocketServerSession, MutableMap<String, Pair<DefaultClientWebSocketSession, HttpClient>>>()
    
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(
        url: String,
        server: DefaultWebSocketServerSession,
    ) {
        val manager = sessions.computeIfAbsent(server) {
            ConnectionsManager(server, http)
        }
        manager.connectOrThrow(url)
    }

    suspend fun closeAllConnections(server: DefaultWebSocketServerSession) {
        val manager = sessions.computeIfAbsent(server) {
            ConnectionsManager(server, http)
        }
        manager.close()
    }

    suspend fun forward(
        url: String,
        request: String,
        server: DefaultWebSocketServerSession
    ) {
        val manager = sessions.computeIfAbsent(server) {
            ConnectionsManager(server, http)
        }
        manager.forwardOrThrow(url, request)
    }
}

