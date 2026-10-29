package com.tonix.wifireader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Camera
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

@Suppress("DEPRECATION")
class ARScreen(ctx: Context, private val targetBssid: String, private val targetSsid: String) : FrameLayout(ctx) {
    private var camera: Camera? = null
    private val h = Handler(Looper.getMainLooper())
    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val overlay: TextView
    private var running = true
    private var receiver: BroadcastReceiver? = null

    init {
        val surface = SurfaceView(ctx)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.holder.setFormat(PixelFormat.TRANSLUCENT)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    camera = Camera.open()
                    camera?.setDisplayOrientation(90)
                    val params = camera?.parameters
                    try {
                        val sizes = params?.supportedPreviewSizes
                        val best = sizes?.minByOrNull { Math.abs(it.width * it.height - 640 * 480) }
                        if (best != null) params.setPreviewSize(best.width, best.height)
                    } catch (e: Exception) { }
                    camera?.parameters = params
                    camera?.setPreviewDisplay(holder)
                    camera?.startPreview()
                } catch (e: Exception) { }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                try { camera?.stopPreview(); camera?.release() } catch (e: Exception) {}
                camera = null
            }
        })

        overlay = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FFC6")); textSize = 18f; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#AA05070D")); setPadding(36, 30, 36, 30)
            text = "Waiting for live scan..."
        }
        addView(overlay, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val note = TextView(ctx).apply {
            text = "Live reading for \"$targetSsid\" — real signal data, updates the instant Android refreshes its scan"
            setTextColor(Color.parseColor("#B2EBF2")); textSize = 11f; gravity = Gravity.CENTER
            setPadding(20, 14, 20, 14); setBackgroundColor(Color.parseColor("#CC05070D"))
        }
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        startLive(ctx)
    }

    private fun update() {
        if (!running) return
        val res = try { wm.scanResults } catch (e: SecurityException) { emptyList() }
        val match = res.find { it.BSSID == targetBssid }
        overlay.text = if (match != null) {
            val mbps = SignalMath.estimateSpeedMbps(match.level, match.frequency)
            val dist = SignalMath.estimateDistanceMeters(match.level)
            "$targetSsid\n${match.level} dBm\n~$mbps Mbps\n~%.1f m".format(dist)
        } else "$targetSsid\nNO SIGNAL"
    }

    private fun startLive(ctx: Context) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { update() }
        }
        try { ctx.applicationContext.registerReceiver(receiver, filter) } catch (e: Exception) { }
        update()
        requestScans()
    }

    private fun requestScans() {
        if (!running) return
        @Suppress("DEPRECATION") wm.startScan()
        h.postDelayed({ requestScans() }, 5000)
    }

    fun stop() {
        running = false
        try { camera?.stopPreview(); camera?.release() } catch (e: Exception) { }
        camera = null
        try { context.applicationContext.unregisterReceiver(receiver) } catch (e: Exception) { }
    }
}
