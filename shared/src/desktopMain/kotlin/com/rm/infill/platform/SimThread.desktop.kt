package com.rm.infill.platform

import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Fair, so the screen waiting on it gets it as soon as the sim lets go between steps. */
actual class SimLock actual constructor() {
    private val lock = ReentrantLock(true)
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
    actual fun tryLock(): Boolean = lock.tryLock()
}

actual val simDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { Thread(it, "sim").apply { isDaemon = true } }.asCoroutineDispatcher()
