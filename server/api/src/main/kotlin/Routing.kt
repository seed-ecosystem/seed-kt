import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
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

interface MessagesRepository {
    suspend fun sendMessage(message: Message): Response
    suspend fun getMessagesByChatId(chatId: String): List<Message>
}

val json = Json { ignoreUnknownKeys = true }

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: Message) {
        val response = messagesRepository.sendMessage(message)
    }

    suspend fun getChatData(chatId: String): List<Message> {

        return messagesRepository.getMessagesByChatId(chatId)
    }
}

class SubscriptionHandler(private val chatService: ChatService) {
    private val subscriptions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun subscribe(session: DefaultWebSocketServerSession, chatId: String, nonce: Int) {

        val messages = chatService.getChatData(chatId)
        
        subscriptions.computeIfAbsent(chatId) { mutableSetOf() }.add(session)
        if (!messages.isEmpty()) {

            if (nonce >= messages.last().nonce) {
                session.send(
                    Frame.Text(
                        json.encodeToString(
                            EventResponseSerialization.serializer(),
                            EventResponseSerialization("event", EventSerialization("wait", chatId))
                        )
                    )
                )
            } else {
                val missingMessages = messages.drop(nonce)
                missingMessages.forEach { message ->
                    val eventResponse = EventResponseSerialization(
                        "event",
                        EventSerialization("new", message.chatId, message)
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
            }
        } else {
            session.send(
                Frame.Text(
                    json.encodeToString(
                        EventResponseSerialization.serializer(),
                        EventResponseSerialization("event", EventSerialization("wait", chatId))
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
                EventSerialization("new", message.chatId, message)
            )
            session.send(Frame.Text(json.encodeToString(EventResponseSerialization.serializer(), eventResponse)))
        }
    }
}

class EventBus(private val chatService: ChatService, private val subscriptionHandler: SubscriptionHandler) {
    suspend fun handleEvent(event: Event, session: DefaultWebSocketServerSession) {
        when (event) {
            is SendEvent -> {
                chatService.addMessage(event.message)
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
        println("WebSocket error: ${e.message} ${e}")
    }
}
