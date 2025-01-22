import controllers.EventBus
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

interface MessagesRepository {
    suspend fun sendMessage(message: Message): WebsocketResponse
    suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<Message>
}

fun Route.messageStream(eventBus: EventBus, json: Json) = webSocket("/ws") {
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val request: Request = try {
                    json.decodeFromString(Request.serializer(), frame.readText())
                } catch (_: Exception) {
                    sendSerialized(WebsocketResponse(response = Response(false)))
                    continue
                }

                val id = request.id 
                val nonce = request.nonce
                val message = request.message

                when (request.type) {
                    "send" -> if (message != null) {
                        val useQueue = message.queueId.isNullOrEmpty()
                        eventBus.handleEvent(SendEvent(chatId = message.id, message = message, useQueue), this)
                    } else {
                        sendSerialized(WebsocketResponse(response = Response(false)))
                    }
                    "subscribe" -> if (id != null && nonce != null) {
                        val useQueue = request.chatId.isNullOrEmpty()
                        eventBus.handleEvent(SubscribeEvent(chatId = id, nonce = nonce, useQueue), this)
                    } else {
                        sendSerialized(WebsocketResponse(response = Response(false)))
                    }
                    "ping" -> eventBus.handleEvent(PingEvent, this)
                    else -> sendSerialized(WebsocketResponse(response = Response(false)))
                }
            }
        }
    } catch (e: Exception) {
        println("WebSocket error: ${e.message} $e")
        sendSerialized(WebsocketResponse(response = Response(false)))
    } finally {
        eventBus.subscriptionHandler.removeSession(this)
    }
}
