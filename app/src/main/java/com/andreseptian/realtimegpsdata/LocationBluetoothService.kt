package com.andreseptian.realtimegpsdata // Mude para o seu pacote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.util.*

class LocationBluetoothService : Service() {

    // --- Constantes e Configurações ---
    companion object {
        private const val TAG = "LocationBTService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationBluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL: Long = 10000 // 10 segundos
        private const val FASTEST_LOCATION_UPDATE_INTERVAL: Long = 5000 // 5 segundos
        
        // TODO: Mude para o endereço MAC do seu ESP32
        private const val ESP32_DEVICE_ADDRESS = "68:25:DD:F1:C1:C2" 

        // TODO: Mude para os UUIDs do seu serviço e característica no ESP32
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    }

    // --- Variáveis de Localização ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // --- Variáveis de Bluetooth ---
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var locationCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // --- Ciclo de Vida do Serviço ---

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Serviço onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Serviço onStartCommand")
        
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        startLocationUpdates()
        startBluetoothScan()

        // Se o sistema matar o serviço, ele será recriado e o onStartCommand será chamado novamente.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço onDestroy")
        stopLocationUpdates()
        stopBluetoothScan()
        disconnectFromDevice()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Não permitimos binding para este serviço
    }

    // --- Lógica de Notificação ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Serviço de Localização e Bluetooth",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("GPS e Bluetooth Ativos")
            .setContentText("Enviando localização para o dispositivo...")
            .setSmallIcon(R.drawable.ic_stat_name) // IMPORTANTE: Crie este ícone em res/drawable
            .setOngoing(true)
            .build()
    }
    
    // --- Lógica de Localização ---

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (checkLocationPermission()) {
            val locationRequest = LocationRequest.create().apply {
                interval = LOCATION_UPDATE_INTERVAL
                fastestInterval = FASTEST_LOCATION_UPDATE_INTERVAL
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Nova localização: Lat: ${location.latitude}, Lon: ${location.longitude}")
                        sendLocationData(location)
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Log.e(TAG, "Permissão de localização não concedida. Parando serviço.")
            stopSelf() // Para o serviço se não tiver permissão
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // --- Lógica de Bluetooth LE ---

    @SuppressLint("MissingPermission")
    private fun startBluetoothScan() {
        if (!checkBluetoothPermissions()) {
             Log.e(TAG, "Permissões de Bluetooth não concedidas. Parando serviço.")
             stopSelf()
             return
        }

        if (isScanning || bluetoothGatt != null) {
            Log.d(TAG, "Já está escaneando ou conectado.")
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(ESP32_DEVICE_ADDRESS)
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, leScanCallback)
        Log.d(TAG, "Iniciando scan para o dispositivo: $ESP32_DEVICE_ADDRESS")

        // Para o scan após um tempo para economizar bateria
        handler.postDelayed({
            if (isScanning) {
                stopBluetoothScan()
                // Se não encontrou, tenta novamente após um tempo
                handler.postDelayed({ startBluetoothScan() }, 10000)
            }
        }, 30000) // 30 segundos de scan
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetoothScan() {
        if (isScanning && bluetoothLeScanner != null && checkBluetoothPermissions()) {
            isScanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
            Log.d(TAG, "Scan parado.")
        }
    }
    
    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "Dispositivo encontrado: ${result.device.address}")
            stopBluetoothScan()
            connectToDevice(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan falhou com o código: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (checkBluetoothPermissions()) {
            Log.d(TAG, "Conectando ao GATT do dispositivo...")
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        }
    }

    private fun disconnectFromDevice() {
        if (bluetoothGatt != null && checkBluetoothPermissions()) {
            Log.d(TAG, "Desconectando do dispositivo...")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            locationCharacteristic = null
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado ao dispositivo GATT.")
                    // Após conectar, busca pelos serviços
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Desconectado do dispositivo GATT.")
                    disconnectFromDevice()
                    // Tenta reconectar
                    handler.postDelayed({ startBluetoothScan() }, 5000)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Serviços descobertos.")
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Serviço UUID não encontrado: $SERVICE_UUID")
                    disconnectFromDevice()
                    return
                }
                locationCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                if (locationCharacteristic == null) {
                    Log.e(TAG, "Característica UUID não encontrada: $CHARACTERISTIC_UUID")
                    disconnectFromDevice()
                } else {
                    Log.i(TAG, "Conexão e configuração bem-sucedidas. Pronto para enviar dados.")
                }
            } else {
                Log.w(TAG, "onServicesDiscovered recebeu: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLocationData(location: Location) {
        if (bluetoothGatt == null || locationCharacteristic == null || !checkBluetoothPermissions()) {
            // Log.w(TAG, "Não é possível enviar dados: GATT não conectado ou característica nula.")
            return
        }

        val lat = location.latitude
        val lon = location.longitude
        val dataString = "LAT:$lat,LON:$lon"
        
        locationCharacteristic?.let { char ->
            char.value = dataString.toByteArray(Charsets.UTF_8)
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val success = bluetoothGatt?.writeCharacteristic(char) ?: false
            Log.d(TAG, "Enviando dados: '$dataString' - Sucesso: $success")
        }
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Para versões mais antigas, as permissões são declaradas no manifest e concedidas na instalação.
            return true
        }
    }
}
