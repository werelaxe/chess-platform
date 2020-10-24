package webserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Config(
    val port: Int,
    val host: String,
    val homeUrl: String
) {
    companion object {
        private val DEFAULT = Config(8080, "0.0.0.0", "http://localhost:8080")

        private fun configNameFromEnv() = when(System.getenv("CHESS_ENV")) {
            "prod" -> "src/main/kotlin/webserver/configs/prod-config"
            else -> "src/main/kotlin/webserver/configs/dev-config"
        }

        fun readOrDefault() = readOrDefault(configNameFromEnv())

        fun readOrDefault(filename: String): Config {
            return try {
                Json.decodeFromString(File(filename).readText())
            } catch (e: Throwable) {
                println(e)
                println(e.stackTrace)
                DEFAULT
            }
        }
    }
}
