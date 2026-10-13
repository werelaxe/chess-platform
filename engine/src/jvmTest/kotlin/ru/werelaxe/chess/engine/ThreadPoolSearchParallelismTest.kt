package ru.werelaxe.chess.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadPoolSearchParallelismTest {
    private val counter = AtomicInteger()
    private val threads = Executors.newFixedThreadPool(2) { Thread(it, "helper-${counter.incrementAndGet()}").apply { isDaemon = true } }
    private val permits = Semaphore(2)
    private val parallelism = ThreadPoolSearchParallelism(threads, permits)

    @Test
    fun spreadsTheItemsOverTheCallerAndTheFreeHelpers() {
        val together = CyclicBarrier(3)
        val threadNames = ConcurrentHashMap.newKeySet<String>()
        val results = parallelism.map(listOf(1, 2, 3)) { item ->
            threadNames += Thread.currentThread().name
            together.await(5, TimeUnit.SECONDS)
            item * 10
        }
        assertEquals(listOf(10, 20, 30), results)
        assertEquals(3, threadNames.size)
        assertTrue(Thread.currentThread().name in threadNames)
        assertEquals(2, permits.availablePermits())
    }

    @Test
    fun borrowsNoMoreHelpersThanThereAreOtherItems() {
        val freeDuringTheRun = CopyOnWriteArrayList<Int>()
        assertEquals(listOf(2, 3), parallelism.map(listOf(1, 2)) { freeDuringTheRun += permits.availablePermits(); it + 1 })
        assertTrue(freeDuringTheRun.all { it >= 1 }, freeDuringTheRun.toString())
        assertEquals(listOf(8), parallelism.map(listOf(7)) { freeDuringTheRun += permits.availablePermits(); it + 1 })
        assertEquals(2, freeDuringTheRun.last())
        assertEquals(2, permits.availablePermits())
    }

    @Test
    fun runsOnTheCallerWhenNoHelperIsFree() {
        permits.acquire(2)
        try {
            val threadNames = HashSet<String>()
            val results = parallelism.map(listOf(1, 2, 3)) { threadNames += Thread.currentThread().name; it + 1 }
            assertEquals(listOf(2, 3, 4), results)
            assertEquals(setOf(Thread.currentThread().name), threadNames)
        } finally {
            permits.release(2)
        }
    }

    @Test
    fun rethrowsOnceEveryStartedItemHasFinished() {
        // Items 0, 1 and 2 start together on the caller and the two helpers; 0 fails, 1 and 2
        // are held back by the gate, 3 is never started.
        val started = CyclicBarrier(3)
        val gate = CountDownLatch(1)
        val run = ConcurrentHashMap.newKeySet<Int>()
        val failure = AtomicReference<Throwable?>()
        val caller = Thread {
            try {
                parallelism.map(listOf(0, 1, 2, 3)) { item ->
                    run += item
                    started.await(5, TimeUnit.SECONDS)
                    if (item == 0) throw IllegalStateException("item 0")
                    gate.await(5, TimeUnit.SECONDS)
                    item
                }
            } catch (e: Throwable) {
                failure.set(e)
            }
        }
        caller.start()
        try {
            caller.join(300)
            assertTrue(caller.isAlive, "the caller returned while items were still running")
            assertEquals(setOf(0, 1, 2), run)
        } finally {
            gate.countDown()
        }
        caller.join(5000)
        assertFalse(caller.isAlive)
        assertEquals("item 0", failure.get()?.message)
        assertEquals(setOf(0, 1, 2), run)
        assertEquals(2, permits.availablePermits())
    }

    @Test
    fun theSharedBoundOnlyGrows() {
        val bound = parallelism.sharedBound(-5)
        parallelism.map((0 until 1000).toList()) { bound.raise(it % 7 - 3) }
        assertEquals(3, bound.get())
        bound.raise(-10)
        assertEquals(3, bound.get())
    }
}
