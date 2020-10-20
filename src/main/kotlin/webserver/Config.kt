package webserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Config(
    val port: Int,
    val host: String
) {
    companion object {
        private val DEFAULT = Config(8080, "0.0.0.0")

        fun readOrDefault(filename: String): Config {
            return try {
                Json.decodeFromString(File(filename).readText())
            } catch (e: Throwable) {
                DEFAULT
            }
        }
    }
}
