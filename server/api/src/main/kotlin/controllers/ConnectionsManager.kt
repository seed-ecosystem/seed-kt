package controllers

import BaseEventResponseSerializable
import ConnectEventSerializable
import ForwardWrapper
import ResponseSerializable
import WebsocketResponseSerializable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.prepareRequest
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

private typealias Server = DefaultWebSocketServerSession
private typealias Connection = Deferred<Client?>
private typealias Client = DefaultClientWebSocketSession

class ConnectionsManager(
    private val json: Json,
    private val server: Server,
    private val http: HttpClient,
) {
    private val mutex: Mutex = Mutex()
    private val connectedUrls: MutableMap<String, Connection> = mutableMapOf()

    suspend fun connectOrThrow(url: String) = mutex.withLock {
        if (url in connectedUrls) {
            server.respondStatusOrThrow(status = false)
            return
        }
        connectedUrls[url] = getConnection(json, server, http, url) {
            mutex.withLock {
                connectedUrls.remove(url)
            }
        }
        server.respondStatusOrThrow(status = true)
    }

    suspend fun forwardOrThrow(
        url: String,
        request: String,
    ) {
        val client = mutex.withLock { connectedUrls[url] }?.await()
        if (client == null) {
            server.respondStatusOrThrow(status = false)
            return
        }
        server.respondStatusOrThrow(status = true)
        runCatching {
            client.send(request)
        }.onFailure {
            client.close()
        }
    }

    suspend fun close() {
        for ((_, connection) in connectedUrls) {
            connection.await()?.close()
        }
    }
}

private fun getConnection(
    json: Json,
    server: Server,
    http: HttpClient,
    url: String,
    onClose: suspend () -> Unit,
): Connection {
    val connection = CompletableDeferred<Client?>()
    http.openConnection(
        url = url,
        scope = server,
        onOpen = connection::complete,
        onMessageOrThrow = { text ->
            server.forwardOrThrow(json, url, text)
        },
        onCloseOrThrow = { 
            onClose()
            server.sendDisconnectedOrThrow(url)
        },
    )
    return connection
}

private suspend fun Server.respondStatusOrThrow(status: Boolean) {
    val response = ResponseSerializable(status)
    val message = WebsocketResponseSerializable("response", response)
    sendSerialized(message)
}

suspend fun Server.forwardOrThrow(
    json: Json,
    url: String,
    string: String,
) {
    val json = try {
        json.parseToJsonElement(string)
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
    val forward = ForwardWrapper(url = url, forward = json)
    sendSerialized(forward)
} 

suspend fun Server.sendDisconnectedOrThrow(url: String) {
    val disconnected = ConnectEventSerializable("disconnected", url)
    val event = BaseEventResponseSerializable("event", disconnected)
    sendSerialized(event)
}

private fun HttpClient.openConnection(
    url: String,
    scope: CoroutineScope,
    onOpen: suspend (Client?) -> Unit,
    onMessageOrThrow: suspend (text: String) -> Unit,
    onCloseOrThrow: suspend () -> Unit,
) = scope.launch {
    runCatching {
        val request = prepareRequest {
            url {
                protocol = URLProtocol.WS
                port = protocol.defaultPort
                takeFrom(url)
            }
        }
        request.body<Client, Unit> { client ->
            onOpen(client)
            for (frame in client.incoming) {
                if (frame !is Frame.Text) continue
                onMessageOrThrow(frame.readText())
            }
        }
    }
    onOpen(null)
    onCloseOrThrow()
}
