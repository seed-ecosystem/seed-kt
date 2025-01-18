import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap


interface MessagesRepository {
    suspend fun sendMessage(message: Message): Response
    suspend fun getMessagesByChatId(chatId: String): List<Message>
}

val json = Json { ignoreUnknownKeys = true }

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: Message): Boolean {
        val response = messagesRepository.sendMessage(message)
        return response.status
    }

    suspend fun getChatData(chatId: String): List<Message> {

        return messagesRepository.getMessagesByChatId(chatId)
    }
}

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, chatId: String, nonce: Long) {

        val messages = chatService.getChatData(chatId).sortedBy { it.nonce }

        subscriptions.computeIfAbsent(chatId) { mutableSetOf() }.add(session)
        if (messages.isNotEmpty()) {
            val lastNonce = messages.last().nonce
            if (nonce >= lastNonce) {
                session.send(
                    Frame.Text(
                        json.encodeToString(
                            EventResponseSerialization.serializer(),
                            EventResponseSerialization("event", EventSerialization("wait", chatId = chatId))
                        )
                    )
                )
            } else {
                val missingMessages = messages.filter { it.nonce >= nonce }.sortedBy { it.nonce }
                missingMessages.forEach { message ->
                    val eventResponse = EventResponseSerialization(
                        "event",
                        EventSerialization("new", message)
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
                            EventResponseSerialization("event", EventSerialization("wait", chatId = chatId))
                        )
                    )
                )
            }
        } else {
            session.send(
                Frame.Text(
                    json.encodeToString(
                        EventResponseSerialization.serializer(),
                        EventResponseSerialization("event", EventSerialization("wait", chatId = chatId))
                    )
                )
            )
        }
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun notifySubscribers(chatId: String, message: Message) {

        subscriptions[chatId]?.forEach { session ->
            val eventResponse = EventResponseSerialization(
                "event",
                EventSerialization("new", message)
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
                subscriptionHandler.notifySubscribers(event.chatId, event.message)
            }
            is PingEvent -> {
                val response = Response("response", status = true)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
            }
            is SubscribeEvent -> {
                val response = Response("response", status = true)
                session.send(Frame.Text(json.encodeToString(Response.serializer(), response)))
                subscriptionHandler.subscribe(session, event.chatId, event.nonce)
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

                when (request.type) {
                    "send" -> if (request.message != null) {
                        eventBus.handleEvent(SendEvent(request.message!!.chatId, request.message!!), this)
                    } else Response("send", false)
                    "ping" -> eventBus.handleEvent(PingEvent, this)
                    "subscribe" -> if (request.chatId != null && request.nonce != null) {
                        eventBus.handleEvent(SubscribeEvent(request.chatId!!, request.nonce!!), this)
                    } else Response("response", false)
                    else -> Response("response", false)
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
