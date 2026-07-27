package com.rp.dedup.core.theme

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object SolarThemeCalculator {

    /**
     * Returns true if the current local time is after sunset or before sunrise,
     * i.e. the app should use the dark theme.
     */
    fun isDarkNow(context: Context): Boolean {
        val (lat, lon) = resolveCoordinates(context)
        val now = Calendar.getInstance()
        val times = sunriseSunsetMinutes(lat, lon, now)
            ?: return now.get(Calendar.HOUR_OF_DAY) < 6 || now.get(Calendar.HOUR_OF_DAY) >= 19

        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return nowMinutes < times.first || nowMinutes >= times.second
    }

    /**
     * Returns (sunriseMinutes, sunsetMinutes) — minutes from local midnight —
     * using the NOAA solar position algorithm. Returns null if polar day/night.
     */
    fun sunriseSunsetMinutes(latDeg: Double, lonDeg: Double, date: Calendar): Pair<Int, Int>? {
        val dayOfYear = date.get(Calendar.DAY_OF_YEAR)
        val tzOffsetHours = date.timeZone.getOffset(date.timeInMillis) / 3_600_000.0

        // Fractional year (radians)
        val gamma = 2 * PI / 365 * (dayOfYear - 1 + (12.0 - 0.5) / 24)

        // Equation of time (minutes)
        val eqtime = 229.18 * (0.000075
                + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
                - 0.014615 * cos(2 * gamma) - 0.04089 * sin(2 * gamma))

        // Solar declination (radians)
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(latDeg)
        // 90.833° zenith = geometric + refraction + solar disc radius
        val cosHA = (cos(Math.toRadians(90.833)) - sin(latRad) * sin(decl)) /
                (cos(latRad) * cos(decl))

        if (cosHA < -1.0 || cosHA > 1.0) return null // polar day/night

        val haDeg = Math.toDegrees(acos(cosHA))

        // Solar noon in minutes from local midnight
        val solarNoon = 720.0 - 4.0 * lonDeg - eqtime + tzOffsetHours * 60

        return Pair(
            (solarNoon - haDeg * 4).toInt().coerceIn(0, 1439),
            (solarNoon + haDeg * 4).toInt().coerceIn(0, 1439)
        )
    }

    // ── Location resolution ────────────────────────────────────────────────────

    private fun resolveCoordinates(context: Context): Pair<Double, Double> {
        lastKnownLocation(context)?.let { return it }
        return timezoneCoordinates()
    }

    private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val maxAgeNs = 24L * 3600 * 1_000_000_000 // 24 hours

        for (provider in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos < maxAgeNs) {
                    return Pair(loc.latitude, loc.longitude)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    /** Derives approximate coordinates from the device timezone when no location is available. */
    private fun timezoneCoordinates(): Pair<Double, Double> {
        val offsetHours = TimeZone.getDefault().rawOffset / 3_600_000.0
        val longitude = offsetHours * 15.0   // 1 hour ≈ 15° longitude
        return Pair(30.0, longitude)          // latitude 30° is a reasonable global mean
    }
}
