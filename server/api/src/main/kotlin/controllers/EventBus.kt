package controllers

import Event
import PingEvent
import Response
import WebsocketResponse
import SendEvent
import SubscribeEvent
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized

class EventBus(private val chatService: ChatService, internal val subscriptionHandler: SubscriptionHandler) {
    suspend fun handleEvent(event: Event, session: DefaultWebSocketServerSession) {
        when (event) {
            is SendEvent -> {
                if (!chatService.addMessage(event.message)) {
                    val websocketResponse = WebsocketResponse(response = Response(false))
                    session.sendSerialized(websocketResponse)
                    return
                }
                val websocketResponse = WebsocketResponse(response = Response(true))
                session.sendSerialized(websocketResponse)
                subscriptionHandler.notifySubscribers(event.chatId, event.message, event.useQueue)
            }
            is PingEvent -> {
                val websocketResponse = WebsocketResponse(response = Response(true))
                session.sendSerialized(websocketResponse)
            }
            is SubscribeEvent -> {
                val websocketResponse = WebsocketResponse(response = Response(true))
                session.sendSerialized(websocketResponse)
                subscriptionHandler.subscribe(session, event.chatId, event.nonce, event.useQueue)
            }
            else -> {
                val websocketResponse = WebsocketResponse(response = Response(false))
                session.sendSerialized(websocketResponse)
            }
        }
    }
}