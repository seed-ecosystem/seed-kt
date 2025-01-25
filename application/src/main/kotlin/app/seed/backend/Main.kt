package app.seed.backend

import MessageTable
import MessagesRepositoryImpl
import controllers.ChatService
import controllers.EventBus
import controllers.ForwardingService
import controllers.SubscriptionHandler
import exceptions.installJson
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import messageStream
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

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val chatService = ChatService(MessagesRepositoryImpl(MessageTable(db)))
    val forwardingService = ForwardingService(json)
    val subscriptionHandler = SubscriptionHandler(chatService)
    val eventBus = EventBus(chatService, subscriptionHandler, forwardingService)

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
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }

        routing {
            messageStream(eventBus, json)
        }
    }
    server.start(wait = true)
}
