package com.rm.infill.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The town's lock. Whatever changes the town or reads it whole holds it: the
 * sim's thread through each day, and the screen for an action or a save. The
 * screen only waits for it for short things; for the rest it tries, and if
 * the sim's busy it tries again next frame.
 */
expect class SimLock() {
    fun lock()
    fun unlock()
    fun tryLock(): Boolean
}

/** Where the town's days and months are worked out: its own thread, off the screen's. */
expect val simDispatcher: CoroutineDispatcher
