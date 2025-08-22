package com.andreseptian.realtimegpsdata

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class BluetoothManager(private val context: Context) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected = false

    // UUID padrão para SPP (Serial Port Profile)
    private val esp32Uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connectToDevice(device: BluetoothDevice, retries: Int, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (isConnected) {
            onSuccess()
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            onFailure(SecurityException("Permissão BLUETOOTH_CONNECT não concedida."))
            return
        }

        Thread {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(esp32Uuid)
                bluetoothSocket?.connect()
                isConnected = true
                onSuccess()
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Connection failed: ${e.message}", e)
                try {
                    bluetoothSocket?.close()
                } catch (e2: IOException) {
                    Log.e("BluetoothManager", "Failed to close socket: ${e2.message}")
                }
                onFailure(e)
            }
        }.start()
    }

    fun sendData(data: String) {
        if (isConnected) {
            try {
                bluetoothSocket?.outputStream?.write(data.toByteArray())
                Log.d("BluetoothManager", "Data sent: $data")
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Failed to send data: ${e.message}", e)
                isConnected = false
            }
        }
    }

    fun closeConnection() {
        try {
            bluetoothSocket?.close()
            isConnected = false
            Log.d("BluetoothManager", "Bluetooth connection closed.")
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Failed to close Bluetooth socket: ${e.message}", e)
        }
    }
}
