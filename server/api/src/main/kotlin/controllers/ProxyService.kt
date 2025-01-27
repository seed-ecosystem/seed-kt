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
    private val connections =
        ConcurrentHashMap<DefaultWebSocketServerSession, MutableMap<String, DefaultClientWebSocketSession>>()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(url: String, serverSession: DefaultWebSocketServerSession) {
        if (connections[serverSession]?.containsKey(url) == true) {
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
                connections.computeIfAbsent(serverSession) { mutableMapOf() }[url] = session

                serverSession.sendSerialized(
                    BaseEventResponseSerializable(
                        "event",
                        ConnectEventSerializable("connected", url)
                    )
                )  // comment it

            } catch (e: Exception) {
                println(e)
                handleDisconnect(url, serverSession)
            }
        }
    }

    suspend fun handleDisconnect(url: String, serverSession: DefaultWebSocketServerSession) {
        connections[serverSession]?.let { urlClientMap ->
            urlClientMap[url]?.let { clientSession ->
                clientSession.close()
                serverSession.sendSerialized(
                    BaseEventResponseSerializable(
                        "event",
                        ConnectEventSerializable("disconnected", url)
                    )
                )
                urlClientMap.remove(url)
            }
        }
    }


    suspend fun closeAllConnections(serverSession: DefaultWebSocketServerSession) {
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
    }

    suspend fun forward(url: String, request: String, serverSession: DefaultWebSocketServerSession) {
        val clientSession = connections[serverSession]?.get(url)
        if (clientSession == null) {
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

    suspend fun pingAllConnections(serverSession: DefaultWebSocketServerSession) {
        connections[serverSession]?.values?.forEach { clientSession ->
            clientSession.send(Frame.Text("""{"type":"ping"}"""))
        }
    }

}
