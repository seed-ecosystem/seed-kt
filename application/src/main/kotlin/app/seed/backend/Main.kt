package app.seed.backend

import ChatService
import EventBus
import SubscriptionHandler
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.coroutineScope
import messageStream
import org.example.exceptions.installJson
import org.jetbrains.exposed.sql.Database

suspend fun main(): Unit = coroutineScope {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8171
    val databaseUrl = System.getenv("DATABASE_URL")
    val databaseUser = System.getenv("DATABASE_USER") ?: ""
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: ""

    val db = Database.connect(
        url = databaseUrl,
        user = databaseUser,
        password = databasePassword
    )
    val chatService = ChatService() // Сервис управления чатами
    val subscriptionHandler = SubscriptionHandler(chatService) // Подписки
    val eventBus = EventBus(chatService, subscriptionHandler)

    val server = this.embeddedServer(CIO, host = "localhost", port = port){
        installJson()
        install(CORS) {
            allowCredentials = true
            anyHost()
            allowHeader(HttpHeaders.ContentType)
        }
        install(PartialContent)
        install(AutoHeadResponse)
        install(DoubleReceive)
        install(WebSockets)

        routing {
            staticResources("/", null)
            
            messageStream(eventBus)
        }
    }
    server.start(wait = true)
}
