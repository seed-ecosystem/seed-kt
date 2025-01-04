import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json

interface MessagesRepository {
    suspend fun sendMessage(message: Message): Response
}

fun Route.messageStream(repository: MessagesRepository) = webSocket("/stream") {
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                val request: Request = try {
                    Json.decodeFromString(Request.serializer(), frame.readText())
                } catch (e: Exception) {
                    send(Frame.Text(Json.encodeToString(Response.serializer(), Response(status = false, code = 400))))
                    continue
                }

                if (request.type == "send" && request.message != null) {
                    val response = repository.sendMessage(request.message!!)
                    send(Frame.Text(Json.encodeToString(Response.serializer(), response)))
                } else {
                    send(Frame.Text(Json.encodeToString(Response.serializer(), Response(status = false, code = 400))))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("WebSocket connection closed")
    } catch (e: Exception) {
        println("Error in messageStream: ${e.localizedMessage}")
        send(Frame.Text(Json.encodeToString(Response.serializer(), Response(status = false, code = 500))))
    }
}
