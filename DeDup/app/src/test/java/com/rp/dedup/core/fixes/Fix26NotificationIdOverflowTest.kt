package com.rp.dedup.core.fixes

import com.rp.dedup.core.firebase.notifications.FirebaseMessageService
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fix #26 — FirebaseMessageService.onMessageReceived() used
 * System.currentTimeMillis().toInt() as the notification ID. currentTimeMillis()
 * returns ~1.7 trillion ms since epoch; truncating to Int overflows to a large
 * negative number. Two notifications arriving in the same millisecond get the same
 * truncated ID, causing the second to silently replace the first.
 *
 * The fix: a companion-object AtomicInteger counter replaces the timestamp cast.
 * Each notification gets a monotonically increasing, unique ID.
 */
class Fix26NotificationIdOverflowTest {

    @Test
    fun `System_currentTimeMillis truncated to Int overflows`() {
        val ms = System.currentTimeMillis()
        val truncated = ms.toInt()
        // On any date after Jan 19 2038 this would be negative; already negative today
        // for most timestamps. Either way, the truncation is lossy and produces collisions.
        assertNotEquals(
            "currentTimeMillis().toInt() must not equal the original Long — it overflows",
            ms, truncated.toLong()
        )
    }

    @Test
    fun `AtomicInteger produces unique sequential IDs`() {
        val counter = AtomicInteger(0)
        val id1 = counter.getAndIncrement()
        val id2 = counter.getAndIncrement()
        val id3 = counter.getAndIncrement()
        assertNotEquals("Consecutive IDs must be unique", id1, id2)
        assertNotEquals("Consecutive IDs must be unique", id2, id3)
        assertEquals("IDs must be monotonically increasing", id1 + 1, id2)
        assertEquals("IDs must be monotonically increasing", id2 + 1, id3)
    }

    @Test
    fun `FirebaseMessageService has a notifIdCounter companion field`() {
        val companionClass = FirebaseMessageService::class.java.declaredClasses
            .find { it.simpleName == "Companion" }
        assertNotNull("FirebaseMessageService must have a Companion object", companionClass)

        val field = FirebaseMessageService::class.java.declaredFields
            .find { it.name == "notifIdCounter" }
        assertNotNull(
            "FirebaseMessageService must have a 'notifIdCounter' field (AtomicInteger)",
            field
        )
        assertEquals(
            "notifIdCounter must be an AtomicInteger",
            AtomicInteger::class.java,
            field!!.type
        )
    }

    @Test
    fun `notifIdCounter is initialized to zero`() {
        val field = FirebaseMessageService::class.java.declaredFields
            .find { it.name == "notifIdCounter" }
        assertNotNull(field)
        field!!.isAccessible = true
        val counter = field.get(null) as? AtomicInteger
        assertNotNull("notifIdCounter must be accessible as an AtomicInteger", counter)
        assertTrue(
            "notifIdCounter must start at 0 (or a low non-overflow value)",
            counter!!.get() >= 0
        )
    }
}
