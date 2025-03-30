package com.andreseptian.realtimegpsdata

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var connectionStatusTextView: TextView
    private lateinit var bluetoothRecyclerView: RecyclerView

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothDeviceAdapter
    private lateinit var locationManager: LocationManager

    private val bluetoothDevices = mutableListOf<BluetoothDevice>()

    // Solicitação de permissões Bluetooth e Localização
    private val bluetoothPermissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val bluetoothPermissionGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                permissions[Manifest.permission.BLUETOOTH_SCAN] == true

        val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (bluetoothPermissionGranted && locationPermissionGranted) {
            startLocationUpdates()
            scanBluetoothDevices()
        } else {
            Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Criando o canal de notificação para o serviço
        createNotificationChannel()

        // Iniciando o serviço para rodar em segundo plano
        val serviceIntent = Intent(this, BluetoothGPSService::class.java)
        startForegroundService(serviceIntent)

        // Vinculando elementos UI
        latitudeTextView = findViewById(R.id.tv_latitude)
        longitudeTextView = findViewById(R.id.tv_longitude)
        speedTextView = findViewById(R.id.tv_speed)
        connectionStatusTextView = findViewById(R.id.tv_connection_status)
        bluetoothRecyclerView = findViewById(R.id.rv_bluetooth_devices)

        // Inicializando o BluetoothManager e LocationManager
        bluetoothManager = BluetoothManager(this)
        locationManager = LocationManager(this)

        // Configurando RecyclerView
        bluetoothAdapter = BluetoothDeviceAdapter(bluetoothDevices) { device ->
            connectToBluetoothDevice(device)
        }
        bluetoothRecyclerView.layoutManager = LinearLayoutManager(this)
        bluetoothRecyclerView.adapter = bluetoothAdapter

        // Botão para iniciar scan de dispositivos Bluetooth
        findViewById<TextView>(R.id.btn_scan_bluetooth).setOnClickListener {
            requestBluetoothPermissions()
        }

        // Botão para parar a conexão Bluetooth
        findViewById<TextView>(R.id.btn_stop_connection).setOnClickListener {
            stopBluetoothConnection()
            stopService(serviceIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "GPS_CHANNEL",
                "Serviço de GPS",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        bluetoothPermissionsRequest.launch(permissions)
    }

    private fun startLocationUpdates() {
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            latitudeTextView.text = "%.5f".format(latitude)
            longitudeTextView.text = "%.5f".format(longitude)
            speedTextView.text = "%.2f m/s".format(speed)

            // Enviando dados GPS para o dispositivo Bluetooth se conectado
            if (::bluetoothManager.isInitialized) {
                val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(latitude, longitude, speed)
                bluetoothManager.sendData(data)
            }
        }
    }

    private fun scanBluetoothDevices() {
        bluetoothDevices.clear()
        bluetoothAdapter.notifyDataSetChanged()

        try {
            bluetoothManager.scanDevices { device ->
                if (!bluetoothDevices.contains(device)) {
                    bluetoothDevices.add(device)
                    bluetoothAdapter.notifyItemInserted(bluetoothDevices.size - 1)
                }
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException: ${e.message}")
            Toast.makeText(this, "Falha ao escanear dispositivos Bluetooth devido à falta de permissões", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        try {
            bluetoothManager.connectToDevice(device, retryCount = 3,
                onConnectionSuccess = {
                    runOnUiThread {
                        connectionStatusTextView.text = "Conectado a ${device.name}"
                        Toast.makeText(this, "Conectado a ${device.name}", Toast.LENGTH_SHORT).show()
                    }
                },
                onConnectionFailed = { exception ->
                    runOnUiThread {
                        connectionStatusTextView.text = "Falha na conexão"
                        Toast.makeText(this, "Falha na conexão: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException: ${e.message}")
            Toast.makeText(this, "Falha ao conectar Bluetooth devido à falta de permissões", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBluetoothConnection() {
        bluetoothManager.closeConnection()
        connectionStatusTextView.text = "Desconectado"
        Toast.makeText(this, "Conexão Bluetooth parada", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
    }
}
