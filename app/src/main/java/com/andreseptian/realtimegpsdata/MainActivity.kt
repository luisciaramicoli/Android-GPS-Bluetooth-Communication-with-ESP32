package com.andreseptian.realtimegpsdata

import android.Manifest
import android.bluetooth.BluetoothDevice
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

    // --- Nome do seu ESP32 ---
    // Substitua 'NOME_DO_SEU_ESP32' pelo nome exato que você configurou no seu ESP32.
    private val esp32Name = "BengalaInteligente" 
    
    // --- Endereço MAC do seu ESP32 (opcional) ---
    // Você pode usar o endereço MAC para uma conexão mais robusta.
    // Substitua 'XX:XX:XX:XX:XX:XX' pelo endereço MAC real.
    // Use um deles (nome OU MAC), mas o MAC é mais confiável.
    private val esp32MacAddress = "68:25:DD:F1:C1:C2" 

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var connectionStatusTextView: TextView
    private lateinit var bluetoothRecyclerView: RecyclerView
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothDeviceAdapter: BluetoothDeviceAdapter
    private lateinit var locationManager: LocationManager

    private val bluetoothDevices = mutableListOf<BluetoothDevice>()
    private var bluetoothReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o serviço de localização
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Inicializa as views
        latitudeTextView = findViewById(R.id.tv_latitude)
        longitudeTextView = findViewById(R.id.tv_longitude)
        speedTextView = findViewById(R.id.tv_speed)
        connectionStatusTextView = findViewById(R.id.tv_connection_status)
        bluetoothRecyclerView = findViewById(R.id.rv_bluetooth_devices)

        bluetoothManager = BluetoothManager(this)
        locationManager = LocationManager(this)

        bluetoothDeviceAdapter = BluetoothDeviceAdapter(bluetoothDevices) { device ->
            connectToBluetoothDevice(device)
        }
        bluetoothRecyclerView.layoutManager = LinearLayoutManager(this)
        bluetoothRecyclerView.adapter = bluetoothDeviceAdapter

        // O botão de escanear agora inicia a descoberta
        findViewById<TextView>(R.id.btn_scan_bluetooth).setOnClickListener {
            ensureBluetoothPermission {
                startDiscoveryForAutoConnect()
            }
        }

        findViewById<TextView>(R.id.btn_stop_connection).setOnClickListener {
            stopBluetoothConnection()
        }
        
        // Garante que as permissões são verificadas e a conexão automática e a localização são iniciadas
        ensureBluetoothPermission {
            autoConnectToEsp32()
            startLocationUpdates()
        }
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
            // Se as permissões forem concedidas, tente a conexão automática e inicie as atualizações de localização
            autoConnectToEsp32()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permissões Bluetooth e/ou de Localização negadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationUpdates() {
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

    // --- FUNÇÃO PARA CONEXÃO AUTOMÁTICA ---
    private fun autoConnectToEsp32() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth não suportado ou desligado", Toast.LENGTH_SHORT).show()
            return
        }

        // Tenta encontrar o ESP32 entre os dispositivos pareados
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val pairedDevices = bluetoothAdapter.bondedDevices
            val esp32Device = pairedDevices.firstOrNull { it.name == esp32Name || it.address == esp32MacAddress }

            if (esp32Device != null) {
                Log.d("MainActivity", "ESP32 encontrado nos pareados: ${esp32Device.name}. Tentando conectar...")
                // Tenta conectar. Se falhar, inicia a busca.
                connectToBluetoothDevice(esp32Device, onFailure = {
                    Log.d("MainActivity", "Conexão com dispositivo pareado falhou. Iniciando busca de descoberta.")
                    startDiscoveryForAutoConnect()
                })
                return
            }
        }
        
        // Se não encontrar nos pareados, inicia a busca (Discovery)
        Log.d("MainActivity", "ESP32 não encontrado nos pareados. Iniciando busca...")
        startDiscoveryForAutoConnect()
    }

    // --- FUNÇÃO DE BUSCA PARA CONEXÃO AUTOMÁTICA ---
    private fun startDiscoveryForAutoConnect() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }
        
        // Garante que a descoberta anterior seja cancelada
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery()
        }

        // Limpa a lista para a nova busca
        bluetoothDevices.clear()
        bluetoothDeviceAdapter.notifyDataSetChanged()

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Se o dispositivo encontrado for o ESP32, conecte e pare a busca
                        if (it.name == esp32Name || it.address == esp32MacAddress) {
                            Log.d("MainActivity", "ESP32 encontrado durante a busca: ${it.name}")
                            connectToBluetoothDevice(it)
                            
                            // Cancela a busca e desregistra o receiver para economizar bateria
                            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                                bluetoothAdapter.cancelDiscovery()
                            }
                            unregisterBluetoothReceiver()
                        } else {
                            // Adiciona outros dispositivos à lista de exibição, como na lógica original
                            if (!bluetoothDevices.contains(it)) {
                                bluetoothDevices.add(it)
                                bluetoothDeviceAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        // Inicia a descoberta
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.startDiscovery()
        }

        // Adiciona um tempo limite para a busca, caso não encontre o ESP32
        Handler(Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.cancelDiscovery()
            }
            unregisterBluetoothReceiver()
            Log.d("MainActivity", "Busca de dispositivos encerrada.")
        }, 30000)
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            unregisterReceiver(it)
            bluetoothReceiver = null
        }
    }

    private fun connectToBluetoothDevice(device: BluetoothDevice, onFailure: (() -> Unit)? = null) {
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
                onFailure?.invoke() // Chama o callback de falha se houver um
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