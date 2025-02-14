package controllers

import BaseRequest
import ConnectEvent
import ConnectRequest
import Event
import ForwardEvent
import ForwardRequest
import ForwardWrapper
import PingEvent
import ResponseSerializable
import SendEvent
import SendRequest
import SubscribeEvent
import SubscribeRequest
import WebsocketResponseSerializable
import io.ktor.http.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class EventBus(
    private val chatService: ChatService,
    internal val subscriptionHandler: SubscriptionHandler,
    internal val forwardingService: ForwardingService
) {
    suspend fun handleEvent(
        event: Event,
        session: DefaultWebSocketServerSession,
        json: Json,
        forwardUrl: String?,
        scope: CoroutineScope
    ) {
        when (event) {
            is SendEvent -> {
                if (!chatService.addMessage(event.message)) {
                    if (forwardUrl == null) {
                        session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                    } else session.sendForwarded(
                        json,
                        WebsocketResponseSerializable(response = ResponseSerializable(false)),
                        forwardUrl
                    )
                    return
                }
                println("Message sent: ${event.message}")
                if (forwardUrl == null) {
                    val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(true))
                    session.sendSerialized(websocketResponse)
                } else session.sendForwarded(
                    json,
                    WebsocketResponseSerializable(response = ResponseSerializable(true)),
                    forwardUrl
                )
                subscriptionHandler.notifySubscribers(event.queueId, event.message, json, forwardUrl)
            }
            is PingEvent -> {
                val status = ResponseSerializable(status = true)
                val response = WebsocketResponseSerializable(response = status)
                if (forwardUrl == null) {
                    session.sendSerialized(response)
                } else {
                    session.sendForwarded(json, response, forwardUrl)
                }
            }
            is SubscribeEvent -> {
                println("Сабскрайб с форвард $forwardUrl пришел")
                if (forwardUrl == null) {
                    val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(true))
                    session.sendSerialized(websocketResponse)
                } else session.sendForwarded(
                    json,
                    WebsocketResponseSerializable(response = ResponseSerializable(true)),
                    forwardUrl
                )
                subscriptionHandler.subscribe(session, event.queueId, event.nonce, json, forwardUrl)
            }
            is ConnectEvent -> {
                forwardingService.connect(event.url, session)
            }
            is ForwardEvent -> {
                forwardingService.forward(event.url, event.request, session)
            }

            else -> {
                val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(false))
                session.sendSerialized(websocketResponse)
            }
        }
    }
    
}

suspend fun handleBaseRequest(
    json: Json,
    jsonString: String,
    baseRequest: BaseRequest,
    eventBus: EventBus,
    session: DefaultWebSocketServerSession,
    forwardUrl: String? = null,
    scope: CoroutineScope
) {
    when (baseRequest.type) {
        "send" -> {
            println("форвард $forwardUrl, строка $jsonString")
            val request = try {
                json.decodeFromString<SendRequest>(jsonString)
            } catch (_: Exception) {
                if (forwardUrl == null) {
                    session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                } else session.sendForwarded(
                    json,
                    WebsocketResponseSerializable(response = ResponseSerializable(false)),
                    forwardUrl
                )
                return
            }
            eventBus.handleEvent(SendEvent(request.message.queueId, request.message), session, json, forwardUrl, scope)
        }

        "subscribe" -> {
            println(jsonString)
            val request = try {
                json.decodeFromString<SubscribeRequest>(jsonString)
            } catch (e: Exception) {
                println("ошибка парса сабскрайба")
                println(e)
                if (forwardUrl == null) {
                    session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                } else session.sendForwarded(
                    json,
                    WebsocketResponseSerializable(response = ResponseSerializable(false)),
                    forwardUrl
                )
                return
            }
            eventBus.handleEvent(SubscribeEvent(request.queueId, nonce = request.nonce), session, json, forwardUrl, scope)
        }

        "connect" -> {
            val request = try {
                json.decodeFromString<ConnectRequest>(jsonString)
            } catch (_: Exception) {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            val url = Url(request.url)
            if (url.host + url.fullPath == "meetacy.app/seed-kt") {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(true)))
                return
            }
           
            eventBus.handleEvent(ConnectEvent(request.url), session, json, forwardUrl, scope)
        }
        
        "forward" -> {
            val websocketRequest = try {
                json.decodeFromString<ForwardRequest>(jsonString)
            } catch (e: Exception) {
                println("$e лол")
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            val forwardRequestString = json.encodeToString(websocketRequest.request)
            val forwardRequestType = try {
                json.decodeFromString<BaseRequest>(forwardRequestString)
            } catch (e: Exception) {
                println(e)
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            if (forwardRequestType.type == "forward" || forwardRequestType.type == "connect") {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            
            val url = Url(websocketRequest.url)
            if (url.host + url.fullPath == "meetacy.app/seed-kt") {
                println("форвард для $url принят, отправлен по рекурсии")
                handleBaseRequest(
                    json,
                    forwardRequestString,
                    forwardRequestType,
                    eventBus,
                    session,
                    websocketRequest.url,
                    scope
                )
                return
            }
            
            eventBus.handleEvent(
                ForwardEvent(
                    websocketRequest.url,
                    forwardRequestString
                ),
                session,
                json,
                websocketRequest.url,
                scope
            )
        }
        
        "ping" -> {
            eventBus.handleEvent(PingEvent, session, json, forwardUrl, scope)
        }

        else -> {
            if (forwardUrl == null) {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
            } else session.sendForwarded(
                json,
                WebsocketResponseSerializable(response = ResponseSerializable(false)),
                forwardUrl
            )
        }
    }
}

suspend inline fun <reified T> DefaultWebSocketServerSession.sendForwarded(
    json: Json,
    originalMessage: T,
    url: String
) {
    val parsedMessage: JsonElement = when (originalMessage) {
        is String -> try {
            val cleanedString = originalMessage.replace("\\\"", "\"").replace("\\n", "")
            json.parseToJsonElement(cleanedString)
        } catch (e: Exception) {
            println("Ошибка обработки строки: ${e.message}")
            JsonPrimitive(originalMessage) 
        }
        is JsonElement -> originalMessage 
        else -> try {

            json.encodeToJsonElement(originalMessage)
        } catch (e: Exception) {
            println("Ошибка сериализации объекта: ${e.message}")
            JsonPrimitive(originalMessage.toString()) 
        }
    }

    val forwardWrapper = ForwardWrapper(url = url, forward = parsedMessage)
    val wrappedJson = json.encodeToString(ForwardWrapper.serializer(), forwardWrapper)
    send(wrappedJson)
} 
