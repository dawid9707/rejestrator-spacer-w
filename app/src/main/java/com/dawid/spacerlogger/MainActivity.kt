package com.dawid.spacerlogger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCalories: TextView
    private lateinit var etWeight: EditText
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnEnd: Button
    private lateinit var btnLocate: Button
    private var routeLine: Polyline? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) onPermissionsGranted()
        else Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        tvCalories = findViewById(R.id.tvCalories)
        etWeight = findViewById(R.id.etWeight)
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnEnd = findViewById(R.id.btnEnd)
        btnLocate = findViewById(R.id.btnLocate)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(GeoPoint(52.23, 21.01)) // Warszawa

        btnStart.setOnClickListener { startWalk() }
        btnPause.setOnClickListener { togglePause() }
        btnEnd.setOnClickListener { endWalk() }
        btnLocate.setOnClickListener { centerOnMyLocation() }

        requestNeededPermissions()
        updateUi()
        updateButtons()
        uiHandler.postDelayed(uiUpdater, 1000)
    }

    private fun startWalk() {
        if (WalkSession.isTracking.get()) return
        val weight = etWeight.text.toString().toFloatOrNull() ?: 70f
        WalkService.start(this, weight)
        updateButtons()
    }

    private fun togglePause() {
        if (!WalkSession.isTracking.get()) return
        if (WalkSession.isPaused()) WalkService.resume(this)
        else WalkService.pause(this)
        updateButtons()
    }

    private fun endWalk() {
        if (!WalkSession.isTracking.get()) return
        WalkService.stop(this)
        saveWalkToHistory()
        updateButtons()
    }

    /** „Zlokalizuj” — wyśrodkuj mapę na ostatniej znanej pozycji GPS. */
    private fun centerOnMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        var best: android.location.Location? = null
        for (provider in lm.getProviders(true)) {
            val loc = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        if (best != null) {
            map.controller.animateTo(GeoPoint(best.latitude, best.longitude))
            map.controller.setZoom(17.0)
        } else {
            Toast.makeText(this, R.string.no_fix, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtons() {
        val tracking = WalkSession.isTracking.get()
        btnStart.isEnabled = !tracking
        btnPause.isEnabled = tracking
        btnEnd.isEnabled = tracking
        btnPause.text = if (WalkSession.isPaused())
            getString(R.string.resume) else getString(R.string.pause)
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onPermissionsGranted()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun onPermissionsGranted() { /* gotowe do startu */ }

    /** Prosty zapis podsumowania spaceru do SharedPreferences. */
    private fun saveWalkToHistory() {
        val prefs = getSharedPreferences("walks", MODE_PRIVATE)
        val history = prefs.getString("history", "") ?: ""
        val entry = getString(R.string.history_entry)
            .format(
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date()),
                WalkSession.distanceMeters.get() / 1000.0,
                WalkSession.calories()
            )
        prefs.edit().putString("history", history + entry + "\n").apply()
        Toast.makeText(this, R.string.walk_saved, Toast.LENGTH_SHORT).show()
    }

    private fun refreshRoute() {
        routeLine?.let { map.overlays.remove(it) }
        routeLine = null
        val pts: List<GeoPoint>
        synchronized(WalkSession.points) { pts = WalkSession.points.toList() }
        if (pts.isNotEmpty()) {
            val line = Polyline(map).apply { setPoints(pts) }
            map.overlays.add(line)
            routeLine = line
            map.controller.setCenter(pts.last())
            map.controller.animateTo(pts.last())
        }
        map.invalidate()
    }

    private val uiUpdater = object : Runnable {
        override fun run() {
            updateUi()
            updateButtons()
            if (WalkSession.isTracking.get()) refreshRoute()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private fun updateUi() {
        tvDistance.text = getString(R.string.distance)
            .format(WalkSession.distanceMeters.get() / 1000.0)
        tvCalories.text = getString(R.string.calories).format(WalkSession.calories())

        val elapsed = WalkSession.elapsedMillis()
        val h = elapsed / 3_600_000
        val m = (elapsed % 3_600_000) / 60_000
        val s = (elapsed % 60_000) / 1000
        tvTime.text = getString(R.string.time).format(h, m, s)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshRoute()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
