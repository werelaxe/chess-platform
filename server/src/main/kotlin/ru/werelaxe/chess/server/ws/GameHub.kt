package ru.werelaxe.chess.server.ws

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import ru.werelaxe.chess.core.ChessJson
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of WebSocket subscribers per game. Each subscriber owns a bounded outbound queue
 * that its socket coroutine drains; a client too slow to keep up loses the oldest events and
 * resynchronizes by refetching the game (the protocol tolerates gaps by comparing `ply`).
 */
class GameHub : GameEvents {
    class Subscription internal constructor(val gameId: String) {
        val outbound: Channel<String> = Channel(capacity = QUEUE_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private val subscribers = ConcurrentHashMap<String, MutableSet<Subscription>>()

    fun subscribe(gameId: String): Subscription {
        val subscription = Subscription(gameId)
        subscribers.computeIfAbsent(gameId) { ConcurrentHashMap.newKeySet() }.add(subscription)
        return subscription
    }

    fun unsubscribe(subscription: Subscription) {
        subscribers.computeIfPresent(subscription.gameId) { _, set ->
            set.remove(subscription)
            set.ifEmpty { null }
        }
        subscription.outbound.close()
    }

    /** Queues an event for one subscriber only (the initial snapshot, pong). */
    fun send(subscription: Subscription, event: ServerEvent) {
        subscription.outbound.trySend(encode(event))
    }

    override fun publish(gameId: String, event: ServerEvent) {
        val targets = subscribers[gameId] ?: return
        val text = encode(event)
        for (subscription in targets) subscription.outbound.trySend(text)
    }

    fun encode(event: ServerEvent): String = ChessJson.json.encodeToString(ServerEvent.serializer(), event)

    fun decode(text: String): ClientMessage? =
        runCatching { ChessJson.json.decodeFromString(ClientMessage.serializer(), text) }.getOrNull()

    private companion object {
        const val QUEUE_CAPACITY = 256
    }
}
