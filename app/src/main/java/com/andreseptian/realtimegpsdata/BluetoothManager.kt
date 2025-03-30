package com.andreseptian.realtimegpsdata

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlin.concurrent.thread
import java.io.OutputStream
import java.util.UUID
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false

    // UUID padrão para Serial Port Profile (SPP)
    private val uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Suppress("unused")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun scanDevices(onDeviceFound: (BluetoothDevice) -> Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let {
                    // Lógica quando encontrar um dispositivo
                    onDeviceFound(it)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BluetoothManager", "Scan failed with error code: $errorCode")
            }
        }

        bluetoothLeScanner.startScan(scanCallback)
    } // Adicionado fechamento da função scanDevices

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: BluetoothDevice,
        retryCount: Int = 3,
        onConnectionSuccess: () -> Unit,
        onConnectionFailed: (Exception) -> Unit
    ) {
        thread {
            var attempts = 0
            var connected = false
            while (attempts < retryCount && !connected) {
                try {
                    // Utiliza UUID padrão para conexão
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuidSpp)
                    bluetoothAdapter?.cancelDiscovery()
                    bluetoothSocket?.connect()
                    outputStream = bluetoothSocket?.outputStream
                    isConnected = true
                    connected = true
                    onConnectionSuccess()
                } catch (e: Exception) {
                    attempts++
                    Log.e("BluetoothManager", "Connection attempt $attempts failed: ${e.message}")
                    closeConnection()
                    if (attempts >= retryCount) {
                        onConnectionFailed(e)
                    }
                }
            }
        }
    }

    fun sendData(data: String) {
        if (isConnected) {
            try {
                outputStream?.write(data.toByteArray())
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Failed to send data: ${e.message}")
            }
        } else {
            Log.e("BluetoothManager", "Bluetooth is not connected")
        }
    }

    fun closeConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while closing connection: ${e.message}")
        }
    }
}
