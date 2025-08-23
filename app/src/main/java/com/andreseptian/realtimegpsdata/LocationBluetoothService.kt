package com.andreseptian.realtimegpsdata

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
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale

class LocationBluetoothService : Service() {

    companion object {
        private const val TAG = "LocationBTService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationBluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        
        // ===================================================================
        // IMPORTANTE: Coloque o endereço MAC do seu ESP32 aqui!
        // ===================================================================
        private const val ESP32_DEVICE_ADDRESS = "68:25:DD:F1:C1:C2"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothManager: BluetoothManager
    private var targetDevice: BluetoothDevice? = null

    // APRIMORAMENTO: Gerenciamento de estado para a conexão automática
    private enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }
    private var autoConnectionState = ConnectionState.DISCONNECTED

    override fun onCreate() {
        super.onCreate()
        locationManager = LocationManager(this)
        bluetoothManager = BluetoothManager(this)

        try {
            val btManagerService = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = btManagerService.adapter
            if (adapter != null && BluetoothAdapter.checkBluetoothAddress(ESP32_DEVICE_ADDRESS)) {
                targetDevice = adapter.getRemoteDevice(ESP32_DEVICE_ADDRESS)
            } else {
                Log.e(TAG, "Endereço MAC inválido ou Bluetooth não suportado.")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter dispositivo Bluetooth: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Iniciando serviço..."))
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            val msg = String.format(Locale.US, "%.6f,%.6f,%.2f\n", latitude, longitude, speed)

            // Lógica de reconexão automática baseada em estado
            if (autoConnectionState == ConnectionState.DISCONNECTED) {
                attemptAutoConnection(msg)
            } else if (autoConnectionState == ConnectionState.CONNECTED) {
                bluetoothManager.sendData(msg)
                if (!bluetoothManager.isConnected) {
                    Log.w(TAG, "Conexão perdida durante o envio. Tentando reconectar.")
                    autoConnectionState = ConnectionState.DISCONNECTED
                }
            }
            // Se estiver CONECTANDO, aguarda a tentativa atual terminar.
        }
    }

    private fun attemptAutoConnection(initialMsg: String) {
        if (targetDevice == null) {
            Log.e(TAG, "Dispositivo alvo (ESP32) não definido.")
            return
        }

        autoConnectionState = ConnectionState.CONNECTING
        updateNotification("Conectando ao ESP32...")
        Log.i(TAG, "Tentando conexão automática com ${targetDevice?.address}")

        targetDevice?.let { device ->
            bluetoothManager.connectToDevice(
                device,
                onConnectionSuccess = {
                    Log.i(TAG, "Conexão automática estabelecida!")
                    autoConnectionState = ConnectionState.CONNECTED
                    updateNotification("Conectado! Enviando localização.")
                    bluetoothManager.sendData(initialMsg)
                },
                onConnectionFailed = { e ->
                    Log.e(TAG, "Falha na conexão automática: ${e.message}")
                    autoConnectionState = ConnectionState.DISCONNECTED
                    updateNotification("Falha ao conectar. Tentando novamente...")
                }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
        Log.d(TAG, "Serviço destruído.")
    }

    // Funções de Notificação e Bind (sem alterações)
    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Serviço de GPS Ativo")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setOngoing(true).build()
    }
    
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Serviço GPS + Bluetooth", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
