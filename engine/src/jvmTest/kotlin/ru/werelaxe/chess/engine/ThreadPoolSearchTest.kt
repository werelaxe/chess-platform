package ru.werelaxe.chess.engine

import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

private val helperThreads = Executors.newFixedThreadPool(4) { Thread(it, "test-helper").apply { isDaemon = true } }

/** The engine contract on the calling thread plus four helper threads. */
class ThreadPoolSearchTest : SearchParallelismContract(ThreadPoolSearchParallelism(helperThreads, Semaphore(4)))
