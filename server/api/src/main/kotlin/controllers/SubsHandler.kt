package controllers

import BaseEventResponseSerializable
import MessageEventSerializable
import MessageSerializable
import QueueEventSerializable
import io.ktor.server.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(
        session: DefaultWebSocketServerSession,
        queueId: String,
        nonce: Long,
        json: Json,
        forwardUrl: String?
    ) {
        val messages = chatService.getChatData(queueId, nonce)

        subscriptions.computeIfAbsent(queueId) { mutableSetOf() }.add(session)

        if (messages.isNotEmpty()) {
            val lastNonce = messages.last().nonce
            if (nonce >= lastNonce) {
                val response = BaseEventResponseSerializable(
                    "event",
                    QueueEventSerializable(
                        "wait",
                        queueId = queueId
                    )
                )
                if (forwardUrl != null) {
                    session.sendForwarded(
                        json,
                        response,
                        forwardUrl
                    )
                } else {
                    session.sendSerialized(response)
                }
            } else {
                messages.forEach { message ->
                    val response = BaseEventResponseSerializable(
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
                    if (forwardUrl != null) {
                        session.sendForwarded(
                            json,
                            response,
                            forwardUrl
                        )
                    } else {
                        session.sendSerialized(response)
                    }
                }
                val response = BaseEventResponseSerializable(
                    "event", QueueEventSerializable(
                        "wait",
                        queueId = queueId
                    )
                )
                if (forwardUrl != null) {
                    session.sendForwarded(
                        json,
                        response,
                        forwardUrl
                    )
                } else {
                    session.sendSerialized(response)
                }
            }
        } else {
            val response = BaseEventResponseSerializable(
                "event", QueueEventSerializable(
                    "wait",
                    queueId = queueId,
                )
            )
            if (forwardUrl != null) {
                session.sendForwarded(
                    json,
                    response,
                    forwardUrl
                )
            } else {
                session.sendSerialized(response)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun notifySubscribers(queueId: String, message: MessageSerializable, json: Json, forwardUrl: String?) {

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
            if (forwardUrl != null) {
                session.sendForwarded(json, eventResponse, forwardUrl)
            } else {
                session.sendSerialized(eventResponse)
            }
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