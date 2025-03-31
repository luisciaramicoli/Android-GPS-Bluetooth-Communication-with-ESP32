package com.andreseptian.realtimegpsdata

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
    private var bluetoothReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serviceIntent = Intent(this, LocationService::class.java)
        startForegroundService(serviceIntent)

        latitudeTextView = findViewById(R.id.tv_latitude)
        longitudeTextView = findViewById(R.id.tv_longitude)
        speedTextView = findViewById(R.id.tv_speed)
        connectionStatusTextView = findViewById(R.id.tv_connection_status)
        bluetoothRecyclerView = findViewById(R.id.rv_bluetooth_devices)

        bluetoothManager = BluetoothManager(this)

        bluetoothDeviceAdapter = BluetoothDeviceAdapter(bluetoothDevices) { device ->
            connectToBluetoothDevice(device)
        }
        bluetoothRecyclerView.layoutManager = LinearLayoutManager(this)
        bluetoothRecyclerView.adapter = bluetoothDeviceAdapter

        findViewById<TextView>(R.id.btn_scan_bluetooth).setOnClickListener {
            ensureBluetoothPermission {
                scanBluetoothDevices()
            }
        }

        findViewById<TextView>(R.id.btn_stop_connection).setOnClickListener {
            stopBluetoothConnection()
        }

        startLocationUpdates()
    }

    private fun ensureBluetoothPermission(onGranted: () -> Unit) {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            scanBluetoothDevices()
        } else {
            Toast.makeText(this, "Permissões Bluetooth negadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
        val locationManager = LocationManager(this)
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            runOnUiThread {
                latitudeTextView.text = "%.5f".format(latitude)
                longitudeTextView.text = "%.5f".format(longitude)
                speedTextView.text = "%.2f m/s".format(speed)
            }
            if (::bluetoothManager.isInitialized) {
                val data = "Latitude: %.5f, Longitude: %.5f, Speed: %.2f m/s".format(latitude, longitude, speed)
                bluetoothManager.sendData(data)
            }
        }
    }

    private fun scanBluetoothDevices() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth não suportado ou desligado", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothDevices.clear()
        bluetoothDeviceAdapter.notifyDataSetChanged()
        bluetoothAdapter.startDiscovery()

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!bluetoothDevices.contains(it)) {
                            bluetoothDevices.add(it)
                            bluetoothDeviceAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothAdapter.cancelDiscovery()
            unregisterBluetoothReceiver()
        }, 30000)
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            unregisterReceiver(it)
            bluetoothReceiver = null
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        try {
            bluetoothManager.connectToDevice(device, 3, {
                runOnUiThread {
                    connectionStatusTextView.text = "Conectado a ${device.name}"
                    Toast.makeText(this, "Conectado a ${device.name}", Toast.LENGTH_SHORT).show()
                }
            }, {
                runOnUiThread {
                    connectionStatusTextView.text = "Conexão falhou"
                    Toast.makeText(this, "Falha: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: SecurityException) {
            Toast.makeText(this, "Erro de permissão Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBluetoothConnection() {
        if (::bluetoothManager.isInitialized) {
            bluetoothManager.closeConnection()
        }
        connectionStatusTextView.text = "Desconectado"
        Toast.makeText(this, "Conexão Bluetooth encerrada", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBluetoothReceiver()
        stopBluetoothConnection()
    }
}
