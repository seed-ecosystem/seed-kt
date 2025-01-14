import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

interface Event
@Serializable
data class SendEvent(val chatId: String, val message: Message) : Event
object PingEvent : Event
@Serializable
data class SubscribeEvent(val userId: String, val chatId: String, val nonce: Int = 0) : Event
@Serializable
data class Request(val type: String, val chatId: String? = null, val message: Message? = null, val nonce: Int? = null)
@Serializable
data class Response(val type: String, val status: Boolean)

val json = Json { ignoreUnknownKeys = true }

class ChatService {
    data class ChatData(val messages: MutableList<Message>, var nonce: Int)
    private val chats = ConcurrentHashMap<String, ChatData>()

    fun addMessage(chatId: String, message: Message) {
        val chat = chats.computeIfAbsent(chatId) { ChatData(mutableListOf(), 0) }
        chat.messages.add(message)
        chat.nonce++
    }

    fun getChatData(chatId: String): ChatData? = chats[chatId]
}

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, chatId: String, nonce: Int) {
        val chat = chatService.getChatData(chatId) ?: ChatService.ChatData(mutableListOf(), 0)

        subscriptions.computeIfAbsent(chatId) { mutableSetOf() }.add(session)

        if (nonce >= chat.nonce) {
            session.send(Frame.Text(json.encodeToString(
                EventResponseSerialization.serializer(), 
                EventResponseSerialization("event", EventSerialization("wait", chatId))))
            )
        } else {
            val missingMessages = chat.messages.subList(nonce, chat.nonce)
            missingMessages.forEach { message ->
                val eventResponse = EventResponseSerialization("event", EventSerialization("new", message.chatId, message))
                session.send(Frame.Text(json.encodeToString(EventResponseSerialization.serializer(), eventResponse)))
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun notifySubscribers(chatId: String, message: Message) {
        subscriptions[chatId]?.forEach { session ->
            GlobalScope.launch {
                val eventResponse = EventResponseSerialization(
                    "event", 
                    EventSerialization("new", message.chatId, message)
                )
                session.send(Frame.Text(json.encodeToString(EventResponseSerialization.serializer(), eventResponse)))
            }
        }
    }
}

class EventBus(private val chatService: ChatService, private val subscriptionHandler: SubscriptionHandler) {
    suspend fun handleEvent(event: Event, session: DefaultWebSocketServerSession) {
        when (event) {
            is SendEvent -> {
                chatService.addMessage(event.chatId, event.message)
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
                        eventBus.handleEvent(SendEvent(request.message.chatId, request.message), this)
                    } else Response("send", false)
                    "ping" -> eventBus.handleEvent(PingEvent, this)
                    "subscribe" -> if (request.chatId != null && request.nonce != null) {
                        eventBus.handleEvent(SubscribeEvent("user", request.chatId, request.nonce), this)
                    } else Response("subscribe", false)
                    else -> Response("unknown", false)
                }
            }
        }
    } catch (e: Exception) {
        println("WebSocket error: ${e.message}")
    }
}
