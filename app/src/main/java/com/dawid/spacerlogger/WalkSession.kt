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
    val paused = AtomicBoolean(false)
    val accumulatedMillis = AtomicLong(0L)
    val finalMillis = AtomicLong(0L)
    @Volatile var weightKg: Float = 70f

    fun start(weight: Float) {
        synchronized(points) { points.clear() }
        distanceMeters.set(0)
        startTimeMillis.set(System.currentTimeMillis())
        accumulatedMillis.set(0L)
        finalMillis.set(0L)
        paused.set(false)
        weightKg = weight
        isTracking.set(true)
    }

    fun stop() {
        if (isTracking.get()) {
            if (paused.get()) finalMillis.set(accumulatedMillis.get())
            else finalMillis.set(
                accumulatedMillis.get() + (System.currentTimeMillis() - startTimeMillis.get())
            )
        }
        isTracking.set(false)
        paused.set(false)
    }

    fun pause() {
        if (isTracking.get() && !paused.get()) {
            accumulatedMillis.addAndGet(System.currentTimeMillis() - startTimeMillis.get())
            paused.set(true)
        }
    }

    fun resume() {
        if (isTracking.get() && paused.get()) {
            startTimeMillis.set(System.currentTimeMillis())
            paused.set(false)
        }
    }

    fun isPaused(): Boolean = paused.get()

    fun addPoint(point: GeoPoint, addedMeters: Double) {
        synchronized(points) { points.add(point) }
        distanceMeters.addAndGet(addedMeters.toLong())
    }

    fun elapsedMillis(): Long =
        if (!isTracking.get()) finalMillis.get()
        else if (paused.get()) accumulatedMillis.get()
        else accumulatedMillis.get() + (System.currentTimeMillis() - startTimeMillis.get())

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
