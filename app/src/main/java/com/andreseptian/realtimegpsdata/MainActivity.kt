package com.andreseptian.realtimegpsdata

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.TextView
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
    private lateinit var bluetoothDeviceAdapter: BluetoothDeviceAdapter

    private val bluetoothDevices = mutableListOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hubungkan elemen UI
        latitudeTextView = findViewById(R.id.tv_latitude)
        longitudeTextView = findViewById(R.id.tv_longitude)
        speedTextView = findViewById(R.id.tv_speed)
        connectionStatusTextView = findViewById(R.id.tv_connection_status)
        bluetoothRecyclerView = findViewById(R.id.rv_bluetooth_devices)

        // Periksa izin lokasi
        val permissionHandler = PermissionHandler(this)
        if (permissionHandler.hasLocationPermission()) {
            startLocationUpdates()
        } else {
            permissionHandler.requestLocationPermission()
        }

        // Inisialisasi BluetoothManager
        bluetoothManager = BluetoothManager(this)

        // Atur RecyclerView
        bluetoothDeviceAdapter = BluetoothDeviceAdapter(bluetoothDevices) { device ->
            connectToBluetoothDevice(device)
        }
        bluetoothRecyclerView.layoutManager = LinearLayoutManager(this)
        bluetoothRecyclerView.adapter = bluetoothDeviceAdapter

        // Scan perangkat Bluetooth
        findViewById<TextView>(R.id.btn_scan_bluetooth).setOnClickListener {
            ensureBluetoothPermission {
                scanBluetoothDevices()
            }
        }

        // Hentikan koneksi Bluetooth
        findViewById<TextView>(R.id.btn_stop_connection).setOnClickListener {
            stopBluetoothConnection()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startLocationUpdates() {
        val locationManager = LocationManager(this)
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            latitudeTextView.text = "%.5f".format(latitude)
            longitudeTextView.text = "%.5f".format(longitude)
            speedTextView.text = "%.2f m/s".format(speed)

            // Kirim data GPS ke perangkat Bluetooth jika terhubung
            if (::bluetoothManager.isInitialized) {
                val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(
                    latitude,
                    longitude,
                    speed
                )
                bluetoothManager.sendData(data)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun scanBluetoothDevices() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não é suportado neste dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
            return
        }

        bluetoothDevices.clear() // Limpa a lista de dispositivos antes de iniciar a descoberta
        bluetoothAdapter.startDiscovery() // Iniciar a descoberta de dispositivos

        // Registrar o receiver para receber eventos de dispositivos encontrados
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Adicionar dispositivo encontrado à lista
                        if (!bluetoothDevices.contains(it)) {
                            bluetoothDevices.add(it)
                            bluetoothDeviceAdapter.notifyDataSetChanged()  // Atualizar a RecyclerView
                        }
                    }
                }
            }
        }

        // Registrar o receiver para eventos de dispositivos encontrados
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        // Parar a descoberta após 30 segundos para liberar recursos
        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothAdapter.cancelDiscovery()
            unregisterReceiver(receiver)
        }, 30000) // 30 segundos
    }

    @SuppressLint("SetTextI18n")
    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        try {
            bluetoothManager.connectToDevice(device, retryCount = 3,
                onConnectionSuccess = {
                    runOnUiThread {
                        connectionStatusTextView.text = "Connected to ${device.name}"
                        Toast.makeText(this, "Connected to ${device.name}", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onConnectionFailed = { exception ->
                    runOnUiThread {
                        connectionStatusTextView.text = "Connection failed"
                        Toast.makeText(
                            this,
                            "Connection failed: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        } catch (e: SecurityException) {
            Log.e("MainActivity", "SecurityException: ${e.message}")
            Toast.makeText(
                this,
                "Bluetooth connection failed due to missing permissions",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun stopBluetoothConnection() {
        bluetoothManager.closeConnection()
        connectionStatusTextView.text = "Disconnected"
        Toast.makeText(this, "Bluetooth connection stopped", Toast.LENGTH_SHORT).show()
    }

    private fun ensureBluetoothPermission(onPermissionGranted: () -> Unit) {
        val permissionHandler = PermissionHandler(this)
        if (permissionHandler.hasBluetoothPermission()) {
            onPermissionGranted()
        } else {
            permissionHandler.requestBluetoothPermissions()
        }
    }

    private fun showPermissionDeniedToast() {
        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionHandler = PermissionHandler(this)
        permissionHandler.handlePermissionResult(
            requestCode,
            grantResults,
            onPermissionGranted = {
                if (requestCode == 101) startLocationUpdates()
                if (requestCode == 102) scanBluetoothDevices()
            },
            onPermissionDenied = { showPermissionDeniedToast() }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val locationManager = LocationManager(this)
        locationManager.stopLocationUpdates()
        bluetoothManager.closeConnection()
    }
}
