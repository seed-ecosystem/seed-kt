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
data class Request(val type: String, val chatId: String? = null, val nonce: Int? = null, val message: Message? = null)

@Serializable
data class EventSerialization(val type: String, val chatId: String? = null, val message: Message? = null)

@Serializable
data class EventResponseSerialization(val type: String, val event: EventSerialization)

data class SubscriptionRequest(
    val type: String,
    val chatId: String,
    val nonce: Long
)