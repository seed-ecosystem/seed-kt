import controllers.EventBus
import controllers.handleBaseRequest
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

fun Route.messageStream(eventBus: EventBus, json: Json) = webSocket("/ws") {
    coroutineScope {
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

                    handleBaseRequest(json, frame.readText(), rawRequest, eventBus, this@webSocket, null, this)
                }
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message} $e")
            sendSerialized(WebsocketResponseSerializable(response = ResponseSerializable(false)))
        } finally {
            eventBus.subscriptionHandler.removeSession(this@webSocket)
            eventBus.forwardingService.closeAllConnections(this@webSocket)
        }
    }
    
}

