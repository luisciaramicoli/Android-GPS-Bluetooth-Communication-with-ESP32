package com.andreseptian.realtimegpsdata

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false

    // UUID padrão para SPP (Serial Port Profile)
    private val uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Suppress("unused")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        device: BluetoothDevice,
        onConnectionSuccess: () -> Unit,
        onConnectionFailed: (Exception) -> Unit
    ) {
        if (isConnected) {
            onConnectionSuccess()
            return
        }

        // Verifica permissões necessárias
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            onConnectionFailed(SecurityException("Permissão BLUETOOTH_CONNECT não concedida."))
            return
        }

        thread {
            try {
                // Tenta fechar qualquer conexão anterior
                closeConnection()

                // Cria o socket e tenta conectar
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuidSpp)
                bluetoothAdapter?.cancelDiscovery() // Cancela a descoberta para otimizar a conexão
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                isConnected = true
                onConnectionSuccess()
                Log.d("BluetoothManager", "Conectado ao dispositivo: ${device.name}")
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Falha na conexão: ${e.message}", e)
                closeConnection()
                onConnectionFailed(e)
            }
        }
    }

    fun sendData(data: String) {
        if (isConnected) {
            try {
                outputStream?.write(data.toByteArray())
                Log.d("BluetoothManager", "Dados enviados: $data")
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Falha ao enviar dados: ${e.message}")
                isConnected = false
            }
        } else {
            Log.e("BluetoothManager", "Bluetooth não está conectado")
        }
    }

    fun closeConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            Log.d("BluetoothManager", "Conexão Bluetooth fechada.")
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Erro ao fechar conexão: ${e.message}")
        }
    }
}
