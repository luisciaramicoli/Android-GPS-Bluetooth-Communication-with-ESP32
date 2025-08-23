package com.andreseptian.realtimegpsdata

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class LocationBluetoothService : Service() {

    companion object {
        private const val TAG = "LocationBTService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationBluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10s
        private const val FASTEST_LOCATION_UPDATE_INTERVAL: Long = 5000 // 5s

        // MAC do ESP32 (ajuste para o seu)
        private const val ESP32_DEVICE_ADDRESS = "68:25:DD:F1:C1:C2"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var bluetoothManager: BluetoothManager
    private var targetDevice: android.bluetooth.BluetoothDevice? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        bluetoothManager = BluetoothManager(this)

        // Obter device pelo MAC
        val adapter =
            (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        targetDevice = adapter?.getRemoteDevice(ESP32_DEVICE_ADDRESS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Conectar ao ESP32
        targetDevice?.let { device ->
            bluetoothManager.connectToDevice(
                device,
                onConnectionSuccess = {
                    Log.d(TAG, "Conectado ao ESP32: ${device.name}")
                },
                onConnectionFailed = { e ->
                    Log.e(TAG, "Erro ao conectar: ${e.message}")
                }
            )
        }

        // Inicia GPS
        startLocationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        bluetoothManager.closeConnection()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Serviço GPS + Bluetooth",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GPS + Bluetooth")
            .setContentText("Enviando localização ao ESP32...")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permissão de localização negada.")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        )
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val msg = "${location.latitude},${location.longitude}\n"
                    Log.d(TAG, "Localização: $msg")
                    bluetoothManager.sendData(msg) // Envia para ESP32
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
