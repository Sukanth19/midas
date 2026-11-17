package dev.suks.midas.ui

import dev.suks.midas.ui.lock.AppLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 0L
    private val lock = AppLock(graceMs = 60_000, now = { now })

    @Test fun `starts locked and unlocks`() {
        assertTrue(lock.onStart(enabled = true))
        lock.unlock()
        assertFalse(lock.locked)
    }

    @Test fun `short trips away keep it unlocked, long ones lock it`() {
        lock.unlock()
        lock.onStop(); now += 30_000
        assertFalse(lock.onStart(enabled = true))
        lock.onStop(); now += 61_000
        assertTrue(lock.onStart(enabled = true))
    }

    @Test fun `turned off never locks`() {
        assertFalse(lock.onStart(enabled = false))
        lock.onStop(); now += 600_000
        assertFalse(lock.onStart(enabled = false))
    }
}
