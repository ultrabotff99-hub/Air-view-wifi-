package com.tonix.wifireader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class TrackerScreen(ctx: Context, private val targetBssid: String, private val targetSsid: String) : LinearLayout(ctx) {
    private val h = Handler(Looper.getMainLooper())
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val signalTxt: TextView
    private val distTxt: TextView
    private var running = true
    private var receiver: BroadcastReceiver? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#05070D"))
        setPadding(40, 100, 40, 40)
        gravity = Gravity.CENTER_HORIZONTAL

        addView(TextView(ctx).apply {
            text = "🎯 TARGETING"; setTextColor(Color.parseColor("#FFC400")); textSize = 13f
        })
        addView(TextView(ctx).apply {
            text = targetSsid; setTextColor(Color.parseColor("#00E5FF")); textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 6, 0, 40)
        })
        signalTxt = TextView(ctx).apply { textSize = 46f; setTextColor(Color.WHITE); setPadding(0, 20, 0, 10) }
        addView(signalTxt)
        distTxt = TextView(ctx).apply { textSize = 16f; setTextColor(Color.parseColor("#7FDBFF")) }
        addView(distTxt)
        addView(TextView(ctx).apply {
            text = "Updates the instant Android refreshes a live scan (the OS limits how often, not this app). Beep speeds up as the signal strengthens."
            setTextColor(Color.parseColor("#80DEEA")); textSize = 11f; gravity = Gravity.CENTER; setPadding(10, 60, 10, 0)
        })
        startLive(ctx)
    }

    private fun update() {
        if (!running) return
        val res = try { wm.scanResults } catch (e: SecurityException) { emptyList() }
        val match = res.find { it.BSSID == targetBssid }
        if (match != null) {
            val rssi = match.level
            signalTxt.text = "$rssi dBm"
            val dist = SignalMath.estimateDistanceMeters(rssi)
            distTxt.text = "~%.1f m away (estimated)".format(dist)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            h.removeCallbacksAndMessages("beep")
            h.postDelayed({ beepLoop(rssi) }, SignalMath.beepIntervalMs(rssi))
        } else {
            signalTxt.text = "NO SIGNAL"
            distTxt.text = "Out of range"
        }
    }

    private fun beepLoop(lastRssi: Int) {
        if (!running) return
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
        h.postDelayed({ beepLoop(lastRssi) }, SignalMath.beepIntervalMs(lastRssi))
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
        try { tone.release() } catch (e: Exception) { }
        try { context.applicationContext.unregisterReceiver(receiver) } catch (e: Exception) { }
    }
}
