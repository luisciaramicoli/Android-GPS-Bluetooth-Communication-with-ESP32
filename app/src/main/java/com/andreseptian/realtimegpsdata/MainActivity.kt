package com.andreseptian.realtimegpsdata

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import com.andreseptian.realtimegpsdata.BluetoothGPSService

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

        createNotificationChannel()

        val serviceIntent = Intent(this, BluetoothGPSService::class.java)
        startForegroundService(serviceIntent)

        latitudeTextView = findViewById(R.id.tv_latitude)
        longitudeTextView = findViewById(R.id.tv_longitude)
        speedTextView = findViewById(R.id.tv_speed)
        connectionStatusTextView = findViewById(R.id.tv_connection_status)
        bluetoothRecyclerView = findViewById(R.id.rv_bluetooth_devices)

        bluetoothManager = BluetoothManager(this)
        locationManager = LocationManager(this)

        bluetoothAdapter = BluetoothDeviceAdapter(bluetoothDevices) { device -> connectToBluetoothDevice(device) }
        bluetoothRecyclerView.layoutManager = LinearLayoutManager(this)
        bluetoothRecyclerView.adapter = bluetoothAdapter

        findViewById<TextView>(R.id.btn_scan_bluetooth).setOnClickListener {
            requestBluetoothPermissions()
        }

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
            Toast.makeText(this, "Falha ao escanear dispositivos Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        bluetoothManager.connectToDevice(device)
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
