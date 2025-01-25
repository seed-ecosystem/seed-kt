package controllers

import MessageSerializable
import MessagesRepository

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: MessageSerializable): Boolean {
        val websocketResponse = messagesRepository.sendMessage(message)
        return websocketResponse.response.status
    }

    suspend fun getChatData(chatId: String, nonce: Long): List<MessageSerializable> {
        return messagesRepository.getMessagesByChatIdAndNonce(chatId, nonce)
    }
}