package com.rp.dedup.core.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SolarThemeCalculatorTest {

    @Test
    fun testSunriseSunsetEquatorEquinox() {
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 20) // Equinox
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val result = SolarThemeCalculator.sunriseSunsetMinutes(0.0, 0.0, date)
        assertNotNull(result)
        val (sunrise, sunset) = result!!
        // Around 06:00 (360 mins) and 18:00 (1080 mins)
        // With equation of time, it will be close to 360 and 1080.
        assertTrue("Sunrise should be close to 6:00", Math.abs(sunrise - 360) < 15)
        assertTrue("Sunset should be close to 18:00", Math.abs(sunset - 1080) < 15)
    }

    @Test
    fun testPolarDayNightReturnsNull() {
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 21) // Winter Solstice
        }
        // At North Pole (lat 85), there's polar night, so no sunrise/sunset.
        val result = SolarThemeCalculator.sunriseSunsetMinutes(85.0, 0.0, date)
        assertNull(result)
    }

    @Test
    fun testIsDarkNowFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Default timezone fallback
        val isDark = SolarThemeCalculator.isDarkNow(context)
        // This should run without crashing and return boolean
        assertNotNull(isDark)
    }
}
