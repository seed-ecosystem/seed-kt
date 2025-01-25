import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class MessageTable(private val db: Database) : Table() {
    private val NONCE = long("NONCE").autoIncrement()
    private val QUEUE_ID = varchar("QUEUE_ID", 344)
    private val SIGNATURE = varchar("SIGNATURE", 344)
    private val CONTENT = varchar("CONTENT", 16384)
    private val CONTENT_IV = varchar("CONTENT_IV", 16)

    init {
        transaction(db) {
            SchemaUtils.create(this@MessageTable)
        }
    }

    suspend fun saveMessage(message: MessageSerializable) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            insert { statement ->
                statement[NONCE] = message.nonce
                statement[QUEUE_ID] = message.queueId
                statement[SIGNATURE] = message.signature
                statement[CONTENT] = message.content
                statement[CONTENT_IV] = message.contentIV
            }
        }

    suspend fun getLastNonce(chatId: String) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            select { (QUEUE_ID eq chatId) }.lastOrNull()?.get(NONCE) 
        }

    suspend fun getMessagesByChatId(chatId: String): List<MessageSerializable> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            select { (QUEUE_ID eq chatId) }
                .map {
                    MessageSerializable(
                        nonce = it[NONCE],
                        queueId = it[QUEUE_ID],
                        signature = it[SIGNATURE],
                        content = it[CONTENT],
                        contentIV = it[CONTENT_IV]
                    )
                }
        }

    suspend fun getMessagesByChatIdAndNonce(chatId: String, nonce: Long): List<MessageSerializable> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            select { (QUEUE_ID eq chatId) and (NONCE greaterEq nonce) }
                .orderBy(NONCE)
                .map {
                    MessageSerializable(
                        nonce = it[NONCE],
                        queueId = it[QUEUE_ID],
                        signature = it[SIGNATURE],
                        content = it[CONTENT],
                        contentIV = it[CONTENT_IV]
                    )
                }
        }
}