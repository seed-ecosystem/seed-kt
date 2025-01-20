import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap


interface MessagesRepository {
    suspend fun sendMessage(message: Message): Response
    suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<Message>
}

val json = Json { ignoreUnknownKeys = true }

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: Message): Boolean {
        val response = messagesRepository.sendMessage(message)
        return response.status
    }

    suspend fun getChatData(chatId: String, nonce: Long): List<Message> {
        return messagesRepository.getMessagesByChatIdAndNonce(chatId, nonce)
    }
}

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, chatId: String, nonce: Long, useQueue: Boolean) {
        val messages = chatService.getChatData(chatId, nonce)
        
        subscriptions.computeIfAbsent(chatId) { mutableSetOf() }.add(session)
        
        if (messages.isNotEmpty()) {
            val lastNonce = messages.last().nonce
            if (nonce >= lastNonce) {
                session.send(
                    Frame.Text(
                        json.encodeToString(
                            EventResponseSerialization.serializer(),
                            EventResponseSerialization(
                                "event",
                                EventSerialization(
                                    "wait",
                                    chatId = if (useQueue) null else chatId,
                                    queueId = if (useQueue) chatId else null,
                                )
                            )
                        )
                    )
                )
            } else {
                messages.forEach { message ->
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
                    session.send(
                        Frame.Text(
                            json.encodeToString(
                                EventResponseSerialization.serializer(),
                                eventResponse
                            )
                        )
                    )
                }
                session.send(
                    Frame.Text(
                        json.encodeToString(
                            EventResponseSerialization.serializer(),
                            EventResponseSerialization("event", EventSerialization(
                                "wait",
                                chatId = if (useQueue) null else chatId,
                                queueId = if (useQueue) chatId else null,
                            ))
                        )
                    )
                )
            }
        } else {
            session.send(
                Frame.Text(
                    json.encodeToString(
                        EventResponseSerialization.serializer(),
                        EventResponseSerialization("event", EventSerialization(
                            "wait",
                            chatId = if (useQueue) null else chatId,
                            queueId = if (useQueue) chatId else null,
                        ))
                    )
                )
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
            session.send(Frame.Text(json.encodeToString(EventResponseSerialization.serializer(), eventResponse)))
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

class EventBus(private val chatService: ChatService, internal val subscriptionHandler: SubscriptionHandler) {
    suspend fun handleEvent(event: Event, session: DefaultWebSocketServerSession) {
        when (event) {
            is SendEvent -> {
                if (!chatService.addMessage(event.message)) {
                    val response = Response("response", false)
                    session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
                    return
                }
                val response = Response("response", status = true)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
                subscriptionHandler.notifySubscribers(event.chatId, event.message, event.useQueue)
            }
            is PingEvent -> {
                val response = Response("response", status = true)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
            }
            is SubscribeEvent -> {
                val response = Response("response", status = true)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
                subscriptionHandler.subscribe(session, event.chatId, event.nonce, event.useQueue)
            }
            else -> {
                val response = Response("response", false)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
            }
        }
    }
}

fun Route.messageStream(eventBus: EventBus) = webSocket("/") {
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val request: Request = try {
                    json.decodeFromString(Request.serializer(), frame.readText())
                } catch (_: Exception) {
                    send(Frame.Text(json.encodeToString(Response.serializer(), Response("response", false))))
                    continue
                }

                val id = request.id 

                when (request.type) {
                    "send" -> if (id != null && request.message != null) {
                        val useQueue = request.chatId.isNullOrEmpty()
                        eventBus.handleEvent(SendEvent(chatId = id, message = request.message!!, useQueue), this)
                    } else {
                        send(Frame.Text(json.encodeToString(Response.serializer(), Response("response", false))))
                    }
                    "subscribe" -> if (id != null && request.nonce != null) {
                        val useQueue = request.chatId.isNullOrEmpty()
                        eventBus.handleEvent(SubscribeEvent(chatId = id, nonce = request.nonce!!, useQueue), this)
                    } else {
                        send(Frame.Text(json.encodeToString(Response.serializer(), Response("response", false))))
                    }
                    "ping" -> eventBus.handleEvent(PingEvent, this)
                    else -> send(Frame.Text(json.encodeToString(Response.serializer(), Response("response", false))))
                }
            }
        }
    } catch (e: Exception) {
        println("WebSocket error: ${e.message} $e")
        send(Frame.Text(json.encodeToString(Response.serializer(), Response("response", false))))
    } finally {
        eventBus.subscriptionHandler.removeSession(this)
    }
}
