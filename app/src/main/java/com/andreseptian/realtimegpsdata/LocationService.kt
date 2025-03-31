package com.andreseptian.realtimegpsdata
import android.os.Bundle
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
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

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initializeBluetooth()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Serviço de Localização Ativo")
            .setContentText("Enviando dados via Bluetooth")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            channelId,
            "Canal de Serviço de Localização",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun initializeBluetooth() {
        val deviceAddress = "00:11:22:33:44:55"  // Substitua pelo endereço do dispositivo Bluetooth
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
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,  // Intervalo de atualização (2 segundos)
                1f,     // Distância mínima para atualização (1 metro)
                locationListener
            )
            Log.d("LocationService", "Iniciando atualizações de localização")
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permissão de localização não concedida: ${e.message}")
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val latitude = location.latitude
            val longitude = location.longitude
            val speed = location.speed

            val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(latitude, longitude, speed)
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        closeBluetoothConnection()
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
}
