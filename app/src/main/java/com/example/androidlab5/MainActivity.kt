package com.example.androidlab5

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.KeyguardManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    private lateinit var lightTextView: TextView

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var powerManager: PowerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Дозвіл на зміну яскравості
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContentView(R.layout.activity_main)

        lightTextView = findViewById(R.id.light_level)

        // Ініціалізація сенсорів
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        releaseProximityWakeLock()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                lightTextView.text = "Освітленість: $lux лк"

                // Автояскравість
                val brightness = (lux / 1000).coerceIn(0.1f, 1f)
                try {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        (brightness * 2000).toInt()
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Немає дозволу на зміну яскравості", Toast.LENGTH_SHORT).show()
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                Log.d("PROXIMITY", "Відстань: $distance")

                if (distance < proximitySensor?.maximumRange ?: 0f) {
                    acquireProximityWakeLock()
                } else {
                    releaseProximityWakeLock()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не використовується
    }

    private fun acquireProximityWakeLock() {
        if (wakeLock == null || wakeLock?.isHeld == false) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "AutoBrightness:ProximityLock"
            )
            wakeLock?.acquire()
            Log.d("WAKELOCK", "WakeLock активовано")
        }
    }

    private fun releaseProximityWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("WAKELOCK", "WakeLock відпущено")
        }
    }
}