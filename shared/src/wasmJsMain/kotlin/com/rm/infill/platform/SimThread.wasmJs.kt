package com.rm.infill.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** A page has only the one thread, so there's nothing to lock against. */
actual class SimLock actual constructor() {
    actual fun lock() {}
    actual fun unlock() {}
    actual fun tryLock(): Boolean = true
}

actual val simDispatcher: CoroutineDispatcher = Dispatchers.Default
