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
    private lateinit var btnToggle: Button
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
        btnToggle = findViewById(R.id.btnToggle)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(GeoPoint(52.23, 21.01))

        btnToggle.setOnClickListener { toggleTracking() }

        requestNeededPermissions()
        updateUi()
        uiHandler.postDelayed(uiUpdater, 1000)
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

    private fun onPermissionsGranted() { }

    private fun toggleTracking() {
        if (WalkSession.isTracking.get()) {
            WalkService.stop(this)
            btnToggle.text = getString(R.string.start)
            saveWalkToHistory()
        } else {
            val weight = etWeight.text.toString().toFloatOrNull() ?: 70f
            WalkService.start(this, weight)
            btnToggle.text = getString(R.string.stop)
        }
        refreshRoute()
    }

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
            refreshRoute()
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

        if (btnToggle.text.isEmpty() || !WalkSession.isTracking.get())
            btnToggle.text = getString(R.string.start)
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
