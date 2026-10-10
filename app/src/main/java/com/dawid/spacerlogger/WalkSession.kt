package com.dawid.spacerlogger

import org.osmdroid.util.GeoPoint
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Wspólny stan sesji spaceru — dostępny zarówno z serwisu, jak i z aktywności.
 */
object WalkSession {
    val points = mutableListOf<GeoPoint>()
    val distanceMeters = AtomicLong(0L)
    val startTimeMillis = AtomicLong(0L)
    val isTracking = AtomicBoolean(false)
    @Volatile var weightKg: Float = 70f

    fun start(weight: Float) {
        synchronized(points) { points.clear() }
        distanceMeters.set(0)
        startTimeMillis.set(System.currentTimeMillis())
        weightKg = weight
        isTracking.set(true)
    }

    fun stop() {
        isTracking.set(false)
    }

    fun addPoint(point: GeoPoint, addedMeters: Double) {
        synchronized(points) { points.add(point) }
        distanceMeters.addAndGet(addedMeters.toLong())
    }

    fun elapsedMillis(): Long =
        if (isTracking.get()) System.currentTimeMillis() - startTimeMillis.get()
        else 0L

    /** Kalorie wg wzoru MET: kcal = MET * masa(kg) * czas(h). MET chodu = 3.5. */
    fun calories(): Double {
        if (distanceMeters.get() == 0L) return 0.0
        val hours = elapsedMillis() / 3_600_000.0
        return 3.5 * weightKg * hours
    }

    /** Odległość między dwoma punktami (wzór haversine), wynik w metrach. */
    fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(la1) * Math.cos(la2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }
}
