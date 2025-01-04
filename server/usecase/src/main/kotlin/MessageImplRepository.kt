import java.util.Base64

class MessagesRepositoryImpl(private val database: MessageTable) : MessagesRepository {
    override suspend fun sendMessage(message: Message): Response {
        with(message) {
            if (!isValidInput(chatId, 256, 344)) return Response("response", false, 0)
            if (!isValidInput(signature, 256, 344)) return Response("response", false, 0)
            if (!isValidInput(contentIV, 12, 16)) return Response("response", false, 0)
            val lastNonce = database.getLastNonce(chatId)
            if (lastNonce + 1 != nonce) return Response("response", false, 1)
            database.saveMessage(message)
            return Response("response", true, null)
        }
    }
}

fun isValidInput(input: String, bytesSize: Int, maxInputLength: Int): Boolean {
    return try {
        val decodedBytes = Base64.getDecoder().decode(input)
        decodedBytes.size == bytesSize
        input.length == maxInputLength
    } catch (e: IllegalArgumentException) {
        false
    }
}