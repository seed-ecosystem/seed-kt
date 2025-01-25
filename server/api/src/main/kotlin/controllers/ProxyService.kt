package controllers

import BaseEventResponseSerializable
import ConnectEventSerializable
import ResponseSerializable
import WebsocketResponseSerializable
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class ForwardingService(val json: Json) {
    private val connections = ConcurrentHashMap<String, DefaultClientWebSocketSession>()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(url: String, serverSession: DefaultWebSocketServerSession) {
        if (connections.containsKey(url)) {
            serverSession.sendSerialized(
                WebsocketResponseSerializable(response = ResponseSerializable(false))
            )
            return
        }

        serverSession.sendSerialized(
            WebsocketResponseSerializable(response = ResponseSerializable(true))
        )
        
        GlobalScope.launch {
            try {
                val client = HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json { explicitNulls = false })
                    }
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(json)
                    }
                }
                val session = client.webSocketSession(urlString = url)
                connections[url] = session
                serverSession.sendSerialized(
                    BaseEventResponseSerializable(
                        "event",
                        ConnectEventSerializable("connected", url)
                    )
                )

                session.incoming.consumeEach { frame ->
                    if (frame is Frame.Close) {
                        println("remove")
                        handleDisconnect(url, serverSession)
                    }
                }
                
            } catch (e: Exception) {
                println(e)
                handleDisconnect(url, serverSession)
            }
        }
    }

    suspend fun handleDisconnect(url: String, serverSession: DefaultWebSocketServerSession) {
        serverSession.sendSerialized(
            BaseEventResponseSerializable(
                "event",
                ConnectEventSerializable("disconnected", url)
            )
        )
        connections.remove(url)?.close()
    }

    suspend fun closeAllConnections() {
        connections.forEach { (url, clientSession) ->
            println("remove")
            clientSession.close()
            connections.remove(url)
        }
    }

    suspend fun forward(url: String, request: String): String? {
        val session = connections[url] ?: return null
        return try {
            session.send(Frame.Text(request))
            val response = session.incoming.receive() as? Frame.Text
            response?.readText()
        } catch (e: Exception) {
            null
        }
    }

    fun selfForward(request: String): String {
        // Локальная обработка запроса
        return request // Тестовая заглушка
    }

    suspend fun pingAllConnections() {
        connections.values.forEach { session ->
            session.send(Frame.Text("""{"type":"ping"}"""))
        }
    }
}
