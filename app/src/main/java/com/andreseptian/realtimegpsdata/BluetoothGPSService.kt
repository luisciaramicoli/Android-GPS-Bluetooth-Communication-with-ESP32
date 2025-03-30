package com.andreseptian.realtimegpsdata

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BluetoothGPSService : Service() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()

        bluetoothManager = BluetoothManager(this)
        locationManager = LocationManager(this)

        createNotificationChannel()

        // Inicia o serviço como foreground
        startForeground(1, createNotification("Serviço rodando em segundo plano"))
        
        // Atualiza localização continuamente
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(latitude, longitude, speed)
            Log.d("BluetoothGPSService", data)
            
            // Envia os dados via Bluetooth se conectado
            if (::bluetoothManager.isInitialized) {
                bluetoothManager.sendData(data)
            }
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "ForegroundServiceChannel")
            .setContentTitle("Serviço Bluetooth GPS")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_gps)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "ForegroundServiceChannel",
            "Serviço em Segundo Plano",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
