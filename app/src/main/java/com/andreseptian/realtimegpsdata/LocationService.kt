package com.andreseptian.realtimegpsdata

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class LocationService : Service() {

    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothManager: BluetoothManager
    private val CHANNEL_ID = "RealtimeGPSDataChannel"

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")
        locationManager = LocationManager(this)
        bluetoothManager = BluetoothManager(this)
        // Inicia o rastreamento de localização e o envio de dados logo após a criação do serviço
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service started")

        // Cria a notificação para o serviço de primeiro plano
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        // Garante que o serviço seja reiniciado se for encerrado pelo sistema
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Retorna null, pois este serviço não está sendo vinculado
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service destroyed")
        // Garante que o rastreamento de localização e a conexão Bluetooth sejam interrompidos
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Serviço de Localização",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Realtime GPS Data")
            .setContentText("O aplicativo está rastreando sua localização em segundo plano.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Substitua por um ícone do seu app
            .build()
    }

    private fun startLocationUpdates() {
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            Log.d("LocationService", "Localização atualizada: $latitude, $longitude, $speed")
            
            // Envia os dados via Bluetooth
            val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(latitude, longitude, speed)
            if (::bluetoothManager.isInitialized) {
                bluetoothManager.sendData(data)
            }
        }
    }
}
