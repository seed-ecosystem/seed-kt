import kotlinx.serialization.Serializable

@Serializable
data class Request(
    val type: String,
    val message: Message? = null,
)

@Serializable
data class Response(
    val type: String = "response",
    val status: Boolean,
    val code: Int? = null,
)

@Serializable
data class Message(
    val nonce: Long ,
    val chatId: String,
    val signature: String,
    val content: String,
    val contentIV: String
)