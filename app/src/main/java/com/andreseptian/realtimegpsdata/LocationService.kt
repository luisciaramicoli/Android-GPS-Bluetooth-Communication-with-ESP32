package com.andreseptian.realtimegpsdata

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.OutputStream
import java.util.UUID

class LocationService : Service() {

    private lateinit var locationManager: LocationManager
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val channelId = "LocationServiceChannel"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var isBluetoothConnected = false
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        acquireWakeLock()
        initializeBluetooth()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Serviço de Localização Ativo")
            .setContentText("Enviando dados via Bluetooth")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Canal de Serviço de Localização",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun initializeBluetooth() {
        val deviceAddress = "A0:A3:B3:19:4D:D2"  // Alterar para o endereço correto
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            bluetoothSocket = device?.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            isBluetoothConnected = true
            Log.d("LocationService", "Bluetooth conectado com sucesso")
        } catch (e: Exception) {
            Log.e("LocationService", "Erro ao conectar ao Bluetooth: ${e.message}")
            isBluetoothConnected = false
        }
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("LocationService", "Permissão de localização não concedida")
                return
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                locationListener
            )
            Log.d("LocationService", "Iniciando atualizações de localização")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Erro ao solicitar atualizações de localização: ${e.message}")
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(
                location.latitude, location.longitude, location.speed
            )
            Log.d("LocationService", "Dados de localização: $data")

            if (isBluetoothConnected) {
                try {
                    outputStream?.write(data.toByteArray())
                    outputStream?.flush()
                    Log.d("LocationService", "Dados enviados via Bluetooth")
                } catch (e: Exception) {
                    Log.e("LocationService", "Erro ao enviar dados Bluetooth: ${e.message}")
                    isBluetoothConnected = false
                }
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "LocationService::WakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        closeBluetoothConnection()
        releaseWakeLock()
        Log.d("LocationService", "Serviço parado")
    }

    private fun closeBluetoothConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d("LocationService", "Conexão Bluetooth encerrada")
        } catch (e: Exception) {
            Log.e("LocationService", "Erro ao fechar conexão Bluetooth: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
