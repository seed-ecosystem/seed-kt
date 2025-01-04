import java.util.Base64

fun main() {
    // Генерация массива из 256 байт
    val byteArray = ByteArray(256) { it.toByte() } // Каждый байт от 0 до 255

    // Кодирование в Base64
    val base64String = Base64.getEncoder().encodeToString(byteArray)

    // Вывод результата
    println("Base64 строка: $base64String")
    println("Длина строки Base64: ${base64String.length}")
}
