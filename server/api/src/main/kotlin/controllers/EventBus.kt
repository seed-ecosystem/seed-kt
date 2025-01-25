package controllers

import BaseRequest
import ConnectEvent
import ConnectRequest
import Event
import PingEvent
import ResponseSerializable
import SendEvent
import SendRequest
import SubscribeEvent
import SubscribeRequest
import WebsocketResponseSerializable
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class EventBus(
    private val chatService: ChatService,
    internal val subscriptionHandler: SubscriptionHandler,
    internal val forwardingService: ForwardingService
) {
    suspend fun handleEvent(event: Event, session: DefaultWebSocketServerSession) {
        when (event) {
            is SendEvent -> {
                if (!chatService.addMessage(event.message)) {
                    val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(false))
                    session.sendSerialized(websocketResponse)
                    return
                }
                val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(true))
                session.sendSerialized(websocketResponse)
                subscriptionHandler.notifySubscribers(event.queueId, event.message)
            }
            is PingEvent -> {
                val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(true))
                session.sendSerialized(websocketResponse)
            }
            is SubscribeEvent -> {
                val websocketResponse = WebsocketResponseSerializable(response = ResponseSerializable(true))
                session.sendSerialized(websocketResponse)
                subscriptionHandler.subscribe(session, event.queueId, event.nonce)
            }
            is ConnectEvent -> {
                forwardingService.connect(event.url, session)
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
    frame: Frame.Text,
    baseRequest: BaseRequest,
    eventBus: EventBus,
    session: DefaultWebSocketServerSession
) {
    when (baseRequest.type) {
        "send" -> {
            val request = try {
                json.decodeFromString<SendRequest>(frame.readText())
            } catch (_: Exception) {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            eventBus.handleEvent(SendEvent(request.message.queueId, request.message), session)
        }

        "subscribe" -> {
            val request = try {
                json.decodeFromString<SubscribeRequest>(frame.readText())
            } catch (_: Exception) {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            eventBus.handleEvent(SubscribeEvent(request.queueId, nonce = request.nonce), session)
        }

        "connect" -> {
            val scope = CoroutineScope(Dispatchers.IO)
            
            val request = try {
                json.decodeFromString<ConnectRequest>( frame.readText())
            } catch (_: Exception) {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
            
            if (request.url == "wss://meetacy.app/seed-kt") {
                session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                return
            }
           
            eventBus.handleEvent(ConnectEvent(request.url), session)
        }
        
        "ping" -> {
            eventBus.handleEvent(PingEvent, session)
        }

        else -> {
            session.sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
        }
    }
}