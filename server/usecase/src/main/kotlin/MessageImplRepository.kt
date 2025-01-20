import java.util.Base64

class MessagesRepositoryImpl(private val database: MessageTable) : MessagesRepository {
    override suspend fun sendMessage(message: Message): Response {
        with(message) {
            if (message.id == null) return Response("response", false, 0)
            if (!isValidInput(id!!, 32, 44)) return Response("response", false, 1)
            if (!isValidInput(signature, 32, 44)) return Response("response", false, 2)
            if (!isValidInput(contentIV, 12, 16)) return Response("response", false, 3)
            if (!isValidContentInput(content, 16384)) return Response("response", false, 4)
            val lastNonce = database.getLastNonce(id!!)
            if (lastNonce == null && message.nonce == 0L) {
                database.saveMessage(message)
                return Response("response", true)
            } else if (lastNonce != null && message.nonce == lastNonce + 1L) {
                database.saveMessage(message)
                return Response("response", true)
            } else {
                return Response("response", false, 5)
            }
            
        }
    }
    override suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<Message> {
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