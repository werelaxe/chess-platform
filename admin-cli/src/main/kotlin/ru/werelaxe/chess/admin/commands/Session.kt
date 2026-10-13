package ru.werelaxe.chess.admin.commands

import com.github.ajalt.clikt.core.CliktError
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import ru.werelaxe.chess.admin.client.AdminApiException
import ru.werelaxe.chess.admin.client.AdminClient
import ru.werelaxe.chess.admin.client.Reply
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

/**
 * What every command needs: the API client, where it points, and whether to print the raw
 * JSON. Failures become [CliktError]s, which the command runner prints to stderr with a
 * non-zero exit code.
 */
class Session(val client: AdminClient, val api: String, val json: Boolean) {
    /** Runs [block] against the API and translates its failures. */
    fun <R> exchange(block: suspend AdminClient.() -> R): R = try {
        runBlocking { client.block() }
    } catch (e: AdminApiException) {
        throw CliktError("Error: ${e.message}")
    } catch (e: HttpRequestTimeoutException) {
        throw CliktError("Error: $api did not answer in time")
    } catch (e: IOException) {
        throw CliktError("Error: cannot reach $api: ${e.message ?: e::class.simpleName}")
    } catch (e: UnresolvedAddressException) {
        throw CliktError("Error: cannot reach $api: unknown host")
    }

    /** The text to print for [replies]: their raw bodies, one per line, with `--json`, else [render] of the decoded values. */
    fun <T> show(replies: List<Reply<T>>, render: (List<T>) -> String): String {
        if (json) return replies.joinToString("\n") { it.raw }
        val values = try {
            replies.map { it.value }
        } catch (e: SerializationException) {
            throw CliktError("Error: unexpected response from $api: ${e.message}")
        }
        return render(values)
    }

    fun <T> show(reply: Reply<T>, render: (T) -> String): String = show(listOf(reply)) { render(it.single()) }
}
