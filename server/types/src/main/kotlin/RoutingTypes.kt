import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebsocketResponseSerializable(
    val type: String = "response",
    val response: ResponseSerializable
)

@Serializable
data class ResponseSerializable(
    val status: Boolean
)

@Serializable
data class MessageSerializable(
    val nonce: Long,
    val queueId: String,
    val signature: String,
    val content: String,
    val contentIV: String
)

@Serializable
data class BaseRequest(
    val type: String 
)

@Serializable
data class SubscribeRequest(
    val type: String = "subscribe",
    val queueId: String,
    val nonce: Long
) 

@Serializable
data class SendRequest(
    val type: String = "send",
    val message: MessageSerializable
) 

@Serializable
data class ConnectRequest(
    val type: String = "connect",
    val url: String
)

@Serializable
data class ForwardRequest(
    val type: String = "forward",
    val url: String,
    val request: JsonElement
)

@Serializable
data class PingRequest(
    val type: String = "ping"
)

@Serializable
data class ForwardWrapper(
    val type: String = "forward",
    val url: String,
    val forward: JsonElement,
)
//Events Response

@Serializable
data class BaseEventResponseSerializable<T>(
    val type: String,
    val event: T
)

@Serializable
data class MessageEventSerializable(
    val type: String,
    val message: MessageSerializable
) 

@Serializable
data class UrlEventSerializable(
    val type: String,
    val url: String
)

@Serializable
data class QueueEventSerializable(
    val type: String,
    val queueId: String
) 

@Serializable
data class ConnectEventSerializable(
    val type: String,
    val url: String
) 

//usecase events

interface Event

data class SendEvent(
    val queueId: String,
    val message: MessageSerializable
) : Event

object PingEvent : Event

data class SubscribeEvent(
    val queueId: String,
    val nonce: Long = 0
) : Event 

data class ConnectEvent(
    val url: String
) : Event

data class ForwardEvent(
    val url: String,
    val request: String
) : Event
