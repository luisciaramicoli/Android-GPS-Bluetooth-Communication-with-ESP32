package com.andreseptian.realtimegpsdata

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ALL_PERMISSIONS_REQUEST_CODE = 101
    }

    // Views da UI
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var btnScanBluetooth: Button
    private lateinit var rvBluetoothDevices: RecyclerView

    // Gerenciadores
    private lateinit var locationManager: LocationManager
    private lateinit var bluetoothManager: BluetoothManager // Gerenciador para conexão manual
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
    }

    // Bluetooth
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private val receiver = BluetoothBroadcastReceiver { device ->
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return@BluetoothBroadcastReceiver
        }
        // Adiciona apenas dispositivos com nome e que não estejam na lista
        if (device.name != null && foundDevices.none { it.address == device.address }) {
            foundDevices.add(device)
            deviceAdapter.notifyItemInserted(foundDevices.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = LocationManager(this)
        bluetoothManager = BluetoothManager(this) // Instancia para uso na Activity

        bindViews()
        setupRecyclerView()
        setupClickListeners()

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, ALL_PERMISSIONS_REQUEST_CODE)
        } else {
            startUpdatingLocationUI()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ALL_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startUpdatingLocationUI()
            } else {
                Toast.makeText(this, "Permissões são necessárias para o app funcionar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bindViews() {
        tvLatitude = findViewById(R.id.tv_latitude)
        tvLongitude = findViewById(R.id.tv_longitude)
        tvSpeed = findViewById(R.id.tv_speed)
        tvStatus = findViewById(R.id.tv_connection_status)
        // CORREÇÃO: IDs dos botões e RecyclerView ajustados.
        // Certifique-se de que seu activity_main.xml tenha estes IDs.
        btnStartService = findViewById(R.id.btn_start_service)
        btnStopService = findViewById(R.id.btn_stop_service)
        btnScanBluetooth = findViewById(R.id.btn_scan_bluetooth)
        rvBluetoothDevices = findViewById(R.id.rv_bluetooth_devices)
    }

    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter(foundDevices) { device ->
            connectToDevice(device) // Conecta ao dispositivo clicado
        }
        rvBluetoothDevices.layoutManager = LinearLayoutManager(this)
        rvBluetoothDevices.adapter = deviceAdapter
    }

    private fun setupClickListeners() {
        btnStartService.setOnClickListener { startLocationService() }
        btnStopService.setOnClickListener { stopLocationService() }
        btnScanBluetooth.setOnClickListener { scanBluetoothDevices() }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothDevices() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(this, "Por favor, ative o Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }
        
        foundDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        Toast.makeText(this, "Procurando dispositivos...", Toast.LENGTH_SHORT).show()
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        tvStatus.text = "Conectando a ${device.name}..."
        bluetoothAdapter?.cancelDiscovery()
        bluetoothManager.connectToDevice(
            device,
            onConnectionSuccess = {
                runOnUiThread {
                    tvStatus.text = "Conectado a ${device.name}!"
                    Toast.makeText(this, "Conexão manual estabelecida.", Toast.LENGTH_SHORT).show()
                }
            },
            onConnectionFailed = { error ->
                runOnUiThread {
                    tvStatus.text = "Falha ao conectar: ${error.message}"
                }
            }
        )
    }

    private fun startUpdatingLocationUI() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        locationManager.startLocationUpdates { latitude, longitude, speed ->
            tvLatitude.text = String.format(Locale.US, "%.6f", latitude)
            tvLongitude.text = String.format(Locale.US, "%.6f", longitude)
            tvSpeed.text = String.format(Locale.US, "%.2f m/s", speed)

            // Envia dados pela conexão manual, se estiver ativa
            if (bluetoothManager.isConnected) {
                val msg = String.format(Locale.US, "%.6f,%.6f,%.2f\n", latitude, longitude, speed)
                bluetoothManager.sendData(msg)
            }
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationBluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        tvStatus.text = "Status: Serviço em segundo plano iniciado."
        Toast.makeText(this, "Iniciando serviço de conexão automática...", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationBluetoothService::class.java)
        stopService(serviceIntent)
        tvStatus.text = "Status: Serviço parado."
        Toast.makeText(this, "Serviço parado.", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.e("MainActivity", "Receiver não registrado.", e)
        }
        bluetoothManager.closeConnection() // Fecha a conexão manual ao sair
    }
}
