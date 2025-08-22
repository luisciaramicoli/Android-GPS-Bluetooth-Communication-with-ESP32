package com.andreseptian.realtimegpsdata // Mude para o seu pacote

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var statusTextView: TextView
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Certifique-se que seu layout tem os IDs corretos

        permissionHandler = PermissionHandler(this)

        statusTextView = findViewById(R.id.tv_connection_status)
        startServiceButton = findViewById(R.id.btn_start_service) // Adicione este botão no seu XML
        stopServiceButton = findViewById(R.id.btn_stop_service)   // Adicione este botão no seu XML

        startServiceButton.setOnClickListener {
            startLocationService()
        }

        stopServiceButton.setOnClickListener {
            stopLocationService()
        }
    }

    private fun startLocationService() {
        permissionHandler.ensureAllPermissions(
            onGranted = {
                val serviceIntent = Intent(this, LocationBluetoothService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                statusTextView.text = "Serviço iniciado. Verifique a notificação."
                Toast.makeText(this, "Iniciando serviço em segundo plano...", Toast.LENGTH_SHORT).show()
            },
            onDenied = {
                Toast.makeText(this, "Permissões necessárias para iniciar o serviço foram negadas.", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationBluetoothService::class.java)
        stopService(serviceIntent)
        statusTextView.text = "Serviço parado."
        Toast.makeText(this, "Serviço parado.", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandler.handlePermissionResult(
            requestCode,
            grantResults,
            onPermissionGranted = {
                // Tenta iniciar o serviço novamente se a permissão foi concedida agora
                startLocationService()
            },
            onPermissionDenied = {
                Toast.makeText(this, "Permissões negadas. O serviço não pode ser iniciado.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
