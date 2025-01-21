// BluetoothManager.kt
package com.andreseptian.realtimegpsdata

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false

    // Gunakan UUID standar untuk Serial Port Profile (SPP)
    private val uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Suppress("unused")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun scanDevices(onDeviceFound: (BluetoothDevice) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("BluetoothManager", "Permission for Bluetooth scanning not granted")
                return
            }
        }

        bluetoothAdapter?.startDiscovery()
        val receiver = BluetoothBroadcastReceiver(onDeviceFound)
        // Receiver ini hanya didaftarkan secara dinamis.
        context.registerReceiver(receiver, BluetoothBroadcastReceiver.intentFilter)
    }

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
                    // Gunakan UUID standar untuk koneksi
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
