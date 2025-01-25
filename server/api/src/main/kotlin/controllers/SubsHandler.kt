package controllers

import BaseEventResponseSerializable
import MessageEventSerializable
import MessageSerializable
import QueueEventSerializable
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.DelicateCoroutinesApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, queueId: String, nonce: Long) {
        val messages = chatService.getChatData(queueId, nonce)

        subscriptions.computeIfAbsent(queueId) { mutableSetOf() }.add(session)

        if (messages.isNotEmpty()) {
            val lastNonce = messages.last().nonce
            if (nonce >= lastNonce) {
                session.sendSerialized(
                    BaseEventResponseSerializable(
                        "event",
                        QueueEventSerializable(
                            "wait",
                            queueId = queueId
                        )
                    )
                )
            } else {
                messages.forEach { message ->
                    session.sendSerialized(BaseEventResponseSerializable(
                        "event",
                        MessageEventSerializable(
                            "new",
                            message = MessageSerializable(
                                message.nonce,
                                queueId,
                                message.signature,
                                message.content,
                                message.contentIV
                            )
                        ))
                    )
                }
                session.sendSerialized(
                    BaseEventResponseSerializable("event", QueueEventSerializable(
                        "wait",
                        queueId = queueId
                    ))
                )
            }
        } else {
            session.sendSerialized(
                BaseEventResponseSerializable("event", QueueEventSerializable(
                    "wait",
                    queueId = queueId,
                ))
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun notifySubscribers(queueId: String, message: MessageSerializable) {

        subscriptions[queueId]?.forEach { session ->
            val eventResponse = BaseEventResponseSerializable(
                "event",
                MessageEventSerializable(
                    "new",
                    message = MessageSerializable(
                        message.nonce,
                        queueId,
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