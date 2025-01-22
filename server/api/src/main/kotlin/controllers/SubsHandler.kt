package controllers

import EventResponseSerialization
import EventSerialization
import Message
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.DelicateCoroutinesApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, chatId: String, nonce: Long, useQueue: Boolean) {
        val messages = chatService.getChatData(chatId, nonce)

        subscriptions.computeIfAbsent(chatId) { mutableSetOf() }.add(session)

        if (messages.isNotEmpty()) {
            val lastNonce = messages.last().nonce
            if (nonce >= lastNonce) {
                session.sendSerialized(
                    EventResponseSerialization(
                        "event",
                        EventSerialization(
                            "wait",
                            chatId = if (useQueue) null else chatId,
                            queueId = if (useQueue) chatId else null,
                        )
                    )
                )
            } else {
                messages.forEach { message ->
                    session.sendSerialized(
                        EventResponseSerialization(
                            "event",
                            EventSerialization(
                                "new",
                                Message(
                                    message.nonce,
                                    if (useQueue) null else chatId,
                                    if (useQueue) chatId else null,
                                    message.signature,
                                    message.content,
                                    message.contentIV
                                )
                            )
                        )
                    )
                }
                session.sendSerialized(
                    EventResponseSerialization("event", EventSerialization(
                        "wait",
                        chatId = if (useQueue) null else chatId,
                        queueId = if (useQueue) chatId else null,
                    ))
                )
            }
        } else {
            session.sendSerialized(
                EventResponseSerialization("event", EventSerialization(
                    "wait",
                    chatId = if (useQueue) null else chatId,
                    queueId = if (useQueue) chatId else null,
                ))
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun notifySubscribers(chatId: String, message: Message, useQueue: Boolean) {

        subscriptions[chatId]?.forEach { session ->
            val eventResponse = EventResponseSerialization(
                "event",
                EventSerialization(
                    "new",
                    Message(
                        message.nonce,
                        if (useQueue) null else chatId,
                        if (useQueue) chatId else null,
                        message.signature,
                        message.content,
                        message.contentIV
                    )
                )
            )
            session.sendSerialized(eventResponse)
        }
    }

    fun removeSession(session: DefaultWebSocketServerSession) {
        subscriptions.forEach { (chatId, sessions) ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                subscriptions.remove(chatId)
            }
        }
    }
}