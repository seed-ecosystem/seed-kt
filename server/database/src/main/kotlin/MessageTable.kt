import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class MessageTable(private val db: Database) : Table() {
    private val NONCE = long("NONCE").autoIncrement()
    private val CHAT_ID = varchar("CHAT_ID", 344)
    private val SIGNATURE = varchar("SIGNATURE", 344)
    private val CONTENT = varchar("CONTENT", 16384)
    private val CONTENT_IV = varchar("CONTENT_IV", 16)

    init {
        transaction(db) {
            SchemaUtils.create(this@MessageTable)
        }
    }

    suspend fun saveMessage(message: Message) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            insert { statement ->
                statement[NONCE] = message.nonce
                statement[CHAT_ID] = message.chatId
                statement[SIGNATURE] = message.signature
                statement[CONTENT] = message.content
                statement[CONTENT_IV] = message.contentIV
            }
        }

    suspend fun getLastNonce(chatId: String) =
        newSuspendedTransaction(Dispatchers.IO, db) {
            select { (CHAT_ID eq chatId) }.lastOrNull()?.get(NONCE)
        }

}