import kotlinx.serialization.Serializable

@Serializable
data class Response(
    val type: String,
    val status: Boolean,
    val code: Int? = null,
)

@Serializable
data class Message(
    val nonce: Long,
    val chatId: String,
    val signature: String,
    val content: String,
    val contentIV: String
)

@Serializable
data class Request(val type: String, val chatId: String? = null, val nonce: Long? = null, val message: Message? = null)

@Serializable
data class EventSerialization(val type: String, val message: Message? = null, val chatId: String? = null)

@Serializable
data class EventResponseSerialization(val type: String, val event: EventSerialization)

interface Event
@Serializable
data class SendEvent(val chatId: String, val message: Message) : Event
object PingEvent : Event
@Serializable
data class SubscribeEvent(val chatId: String, val nonce: Long = 0) : Event