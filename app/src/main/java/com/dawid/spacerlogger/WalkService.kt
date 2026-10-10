package com.dawid.spacerlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.osmdroid.util.GeoPoint

class WalkService : Service() {

    private var lastPoint: GeoPoint? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                WalkSession.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val weight = intent?.getFloatExtra(EXTRA_WEIGHT, 70f) ?: 70f
                WalkSession.start(weight)
                startForeground(NOTIF_ID, buildNotification())
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                2f,
                locationListener
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private val locationListener = LocationListener { location: Location ->
        val point = GeoPoint(location.latitude, location.longitude)
        val last = lastPoint
        if (last != null) {
            val d = WalkSession.haversine(last, point)
            if (d >= 1.0 && d <= 100.0) {
                WalkSession.addPoint(point, d)
                lastPoint = point
            }
        } else {
            WalkSession.addPoint(point, 0.0)
            lastPoint = point
        }
        updateNotification()
    }

    private fun channel(): String {
        val id = "walk_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(id, "Rejestracja spaceru", NotificationManager.IMPORTANCE_LOW)
        )
        return id
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, channel())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .build()

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(this, channel())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                getString(R.string.notif_progress)
                    .format(WalkSession.distanceMeters.get() / 1000.0)
            )
            .setOngoing(true)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    override fun onDestroy() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try { lm.removeUpdates(locationListener) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.dawid.spacerlogger.STOP"
        const val EXTRA_WEIGHT = "weight"
        const val NOTIF_ID = 1

        fun start(context: Context, weight: Float) {
            val i = Intent(context, WalkService::class.java)
                .putExtra(EXTRA_WEIGHT, weight)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WalkService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
