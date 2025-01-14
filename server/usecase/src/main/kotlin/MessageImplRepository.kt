//import java.util.Base64
//
//class MessagesRepositoryImpl(private val database: MessageTable) : MessagesRepository {
//    override suspend fun sendMessage(message: Message): Response {
//        with(message) {
//            if (!isValidInput(chatId, 32, 44)) return Response("response", false, 1)
//            if (!isValidInput(signature, 32, 44)) return Response("response", false, 2)
//            if (!isValidInput(contentIV, 12, 16)) return Response("response", false, 3)
//            val lastNonce = database.getLastNonce(chatId)
//            if (lastNonce == null && nonce == 0L) {
//                database.saveMessage(message)
//                return Response("response", true, null)
//            } else if (lastNonce != null && lastNonce + 1 != nonce) {
//                return Response("response", false, 4)
//            } else {
//                return Response("response", false, null)
//            }
//        }
//    }
//}
//
//fun isValidInput(input: String, bytesSize: Int, maxInputLength: Int): Boolean {
//    return try {
//        val decodedBytes = Base64.getDecoder().decode(input)
//        decodedBytes.size == bytesSize
//        input.length == maxInputLength
//    } catch (e: IllegalArgumentException) {
//        false
//    }
//}