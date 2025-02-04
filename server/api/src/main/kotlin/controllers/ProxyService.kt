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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class ForwardingService(val json: Json) {
    private val connections =
        ConcurrentHashMap<DefaultWebSocketServerSession, MutableMap<String, DefaultClientWebSocketSession>>()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(url: String, serverSession: DefaultWebSocketServerSession, scope: CoroutineScope) {
        if (connections[serverSession]?.containsKey(url) == true) {
            serverSession.sendSerialized(
                WebsocketResponseSerializable(response = ResponseSerializable(false))
            )
            return
        }
        
        scope.launch {
            try {
                val client = HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json { explicitNulls = false })
                    }
                    install(WebSockets) {
                        contentConverter = KotlinxWebsocketSerializationConverter(json)
                    }
                }
                val clientSession = try {
                    client.webSocketSession(urlString = url)
                } finally {
                    serverSession.sendSerialized(
                        WebsocketResponseSerializable(response = ResponseSerializable(true))
                    )
                }
                clientSession.outgoing.invokeOnClose {
                    launch {
                        handleDisconnect(url, serverSession, true)
                    }
                }
                connections.computeIfAbsent(serverSession) { mutableMapOf() }[url] = clientSession
                
            } catch (_: Exception) {
                handleDisconnect(url, serverSession, false)
            }
        }
    }

    suspend fun handleDisconnect(url: String, serverSession: DefaultWebSocketServerSession, wasConnected: Boolean) {
        serverSession.sendSerialized(
            BaseEventResponseSerializable(
                "event",
                ConnectEventSerializable("disconnected", url)
            )
        )
        if (wasConnected) {
            connections[serverSession]?.remove(url)
        }
    }
    
    suspend fun closeAllConnections(serverSession: DefaultWebSocketServerSession) {
        try {
            connections[serverSession]?.let { urlClientMap ->
                urlClientMap.forEach { (url, clientSession) ->
                    clientSession.close()
                    println("Closing connection to $url")
                    serverSession.sendSerialized(
                        BaseEventResponseSerializable(
                            "event",
                            ConnectEventSerializable("disconnected", url)
                        )
                    )
                }
                connections.remove(serverSession)
            }
        } catch (e: Exception) {
            println("CloseAllConnection")
            println(e)
        }
        
    }

    suspend fun forward(url: String, request: String, serverSession: DefaultWebSocketServerSession) {
        val clientSession = connections[serverSession]?.get(url)
        if (clientSession == null) {
            println(connections[serverSession])
            println(url)
            serverSession.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
            return
        }

        try {
            clientSession.send(request)
            clientSession.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val responseText = frame.readText()
                    print(responseText)
                    serverSession.sendForwarded(json, responseText, url)
                }
            }
        } catch (e: Exception) {
            println("mem")
            println(e)
            serverSession.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
            return
        }

    }

    suspend fun pingAllConnections(serverSession: DefaultWebSocketServerSession, forwardUrl: String? = null) {
        try {
            connections[serverSession]?.forEach { (_, clientSession) ->
                clientSession.send(Frame.Text("""{"type":"ping"}"""))
            }
        } finally {
            if (forwardUrl == null) {
                serverSession.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(true)))
            } else
                serverSession.sendForwarded(json, ResponseSerializable(true), forwardUrl)
        }
        
    }

}
