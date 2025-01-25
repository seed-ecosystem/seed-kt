import java.util.Base64

class MessagesRepositoryImpl(private val database: MessageTable) : MessagesRepository {
    override suspend fun sendMessage(message: MessageSerializable): WebsocketResponseSerializable {
        with(message) {
            if (!isValidInput(queueId, 32, 44)) return WebsocketResponseSerializable(response = ResponseSerializable(false))
            if (!isValidInput(signature, 32, 44)) return WebsocketResponseSerializable(response = ResponseSerializable(false))
            if (!isValidInput(contentIV, 12, 16)) return WebsocketResponseSerializable(response = ResponseSerializable(false))
            if (!isValidContentInput(content, 16384)) return WebsocketResponseSerializable(response = ResponseSerializable(false))
                val lastNonce = database.getLastNonce(queueId)
            if (lastNonce == null && message.nonce == 0L) {
                database.saveMessage(message)
                return WebsocketResponseSerializable(response = ResponseSerializable(true))
            } else if (lastNonce != null && message.nonce == lastNonce + 1L) {
                database.saveMessage(message)
                return WebsocketResponseSerializable(response = ResponseSerializable(true))
            } else {
                return WebsocketResponseSerializable(response = ResponseSerializable(false))
            }
            
        }
    }
    override suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<MessageSerializable> {
        return database.getMessagesByChatIdAndNonce(chatId, nonce)
    }
}

fun isValidInput(input: String, bytesSize: Int, maxInputLength: Int): Boolean {
    return try {
        val decodedBytes = Base64.getDecoder().decode(input)
        decodedBytes.size == bytesSize
        input.length == maxInputLength
    } catch (_: IllegalArgumentException) {
        false
    }
}

fun isValidContentInput(input: String, maxInputLength: Int): Boolean {
    return try {
        Base64.getDecoder().decode(input)
        input.length <= maxInputLength
    } catch (_: IllegalArgumentException) {
        false
    }
}