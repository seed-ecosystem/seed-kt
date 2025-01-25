import controllers.EventBus
import controllers.handleBaseRequest
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

interface MessagesRepository {
    suspend fun sendMessage(message: MessageSerializable): WebsocketResponseSerializable
    suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<MessageSerializable>
}

fun Route.messageStream(eventBus: EventBus, json: Json) = webSocket("/ws") {
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val rawRequest: BaseRequest = try {
                    json.decodeFromString<BaseRequest>(frame.readText())
                } catch (e: Exception) {
                    println(e)
                    sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
                    continue
                }
                
                handleBaseRequest(json, frame, rawRequest, eventBus, this)
            }
        }
    } catch (e: Exception) {
        println("WebSocket error: ${e.message} $e")
        sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
    } finally {
        eventBus.subscriptionHandler.removeSession(this)
        eventBus.forwardingService.closeAllConnections()
    }
}

