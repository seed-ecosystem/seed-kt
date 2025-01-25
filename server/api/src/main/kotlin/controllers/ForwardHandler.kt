package controllers
//
//import io.ktor.server.websocket.DefaultWebSocketServerSession
//import io.ktor.websocket.Frame
//import io.ktor.websocket.close
//import io.ktor.websocket.readText
//import java.util.concurrent.ConcurrentHashMap
//
//class ForwardingService {
//    private val connections = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
//
//    suspend fun connect(url: String): Boolean {
//        if (connections.containsKey(url)) return false
//        return try {
//            val client = HttpClient(CIO) {
//                install(WebSockets)
//            }
//            val session = client.webSocketSession(url)
//            connections[url] = session
//            true
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    suspend fun forward(url: String, request: String): String? {
//        val session = connections[url] ?: return null
//        return try {
//            session.send(Frame.Text(request))
//            val response = session.incoming.receive() as? Frame.Text
//            response?.readText()
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    suspend fun handleDisconnect(url: String) {
//        connections.remove(url)?.close()
//    }
//
//    fun selfForward(request: String): String {
//        // Локальная обработка запроса
//        return request // Тестовая заглушка
//    }
//
//    fun pingAllConnections() {
//        connections.values.forEach { session ->
//            session.send(Frame.Text("""{"type":"ping"}"""))
//        }
//    }
//}
