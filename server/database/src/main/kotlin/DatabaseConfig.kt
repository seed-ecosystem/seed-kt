sealed interface DatabaseConfig {
    data class Connection(
        val url: String,
        val user: String,
        val password: String
    ) : DatabaseConfig
    
}