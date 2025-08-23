package com.andreseptian.realtimegpsdata // Mude para o seu pacote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class LocationBluetoothService : Service() {

    companion object {
        private const val TAG = "LocationBTService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationBluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1

        // IMPORTANTE: Altere para o endereço MAC do seu dispositivo ESP32
        private const val ESP32_DEVICE_ADDRESS = "68:25:DD:F1:C1:C2"
    }

    // Gerenciadores para cada responsabilidade
    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothManager: BluetoothManager
    private var targetDevice: android.bluetooth.BluetoothDevice? = null

    override fun onCreate() {
        super.onCreate()
        // Instancia os gerenciadores
        locationManager = LocationManager(this)
        bluetoothManager = BluetoothManager(this)

        try {
            val bluetoothManagerService = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = bluetoothManagerService.adapter
            
            if (adapter == null) {
                Log.e(TAG, "Este dispositivo não suporta Bluetooth.")
                stopSelf()
                return
            }

            if (BluetoothAdapter.checkBluetoothAddress(ESP32_DEVICE_ADDRESS)) {
                targetDevice = adapter.getRemoteDevice(ESP32_DEVICE_ADDRESS)
            } else {
                Log.e(TAG, "Endereço MAC do ESP32 é inválido: $ESP32_DEVICE_ADDRESS")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar o Bluetooth: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification("Buscando conexão e localização...")
        startForeground(NOTIFICATION_ID, notification)

        startLocationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
        Log.d(TAG, "Serviço destruído.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissão de localização negada. O serviço não pode iniciar.")
            stopSelf()
            return
        }

        locationManager.startLocationUpdates { latitude, longitude, speed ->
            val dataString = String.format("%.6f,%.6f,%.2f", latitude, longitude, speed)
            val msg = "$dataString\n"
            Log.d(TAG, "Dados: $dataString")

            // Lógica de reconexão automática
            if (!bluetoothManager.isConnected) {
                updateNotification("Desconectado. Tentando conectar ao ESP32...")
                Log.w(TAG, "Bluetooth desconectado. Tentando reconectar...")
                
                targetDevice?.let { device ->
                    bluetoothManager.connectToDevice(
                        device,
                        onConnectionSuccess = {
                            Log.i(TAG, "Reconectado com sucesso ao ESP32.")
                            updateNotification("Conectado! Enviando dados.")
                            bluetoothManager.sendData(msg)
                        },
                        onConnectionFailed = { e ->
                            Log.e(TAG, "Falha ao reconectar: ${e.message}")
                        }
                    )
                }
            } else {
                // Se já estiver conectado, apenas envia o dado
                bluetoothManager.sendData(msg)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Serviço de Localização e Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Serviço de GPS Ativo")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_name) // Certifique-se que este ícone existe
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
