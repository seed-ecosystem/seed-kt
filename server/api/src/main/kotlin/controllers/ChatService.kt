package controllers

import Message
import MessagesRepository

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: Message): Boolean {
        val websocketResponse = messagesRepository.sendMessage(message)
        return websocketResponse.response.status
    }

    suspend fun getChatData(chatId: String, nonce: Long): List<Message> {
        return messagesRepository.getMessagesByChatIdAndNonce(chatId, nonce)
    }
}