package controllers

import MessageSerializable
import WebsocketResponseSerializable

class ChatService(private val messagesRepository: MessagesRepository) {
    suspend fun addMessage(message: MessageSerializable): Boolean {
        val websocketResponse = messagesRepository.sendMessage(message)
        return websocketResponse.response.status
    }

    suspend fun getChatData(chatId: String, nonce: Long): List<MessageSerializable> {
        return messagesRepository.getMessagesByChatIdAndNonce(chatId, nonce)
    }
}

interface MessagesRepository {
    suspend fun sendMessage(message: MessageSerializable): WebsocketResponseSerializable
    suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<MessageSerializable>
}