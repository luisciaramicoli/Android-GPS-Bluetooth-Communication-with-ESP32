package com.andreseptian.realtimegpsdata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Classe utilitária para gerenciar as permissões de localização e Bluetooth.
 * Simplifica a verificação e solicitação de permissões de tempo de execução.
 */
class PermissionHandler(private val activity: AppCompatActivity) {

    companion object {
        // Códigos para identificar os pedidos de permissão
        const val ALL_PERMISSIONS_REQUEST_CODE = 101
    }

    /**
     * Verifica e solicita todas as permissões necessárias de uma vez.
     *
     * @param onGranted Callback a ser executado se todas as permissões forem concedidas.
     * @param onDenied Callback a ser executado se alguma permissão for negada.
     */
    fun ensureAllPermissions(onGranted: () -> Unit, onDenied: () -> Unit) {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Permissões específicas para o Android 12 (API 31) e superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Permissões para APIs mais antigas
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Permissão de localização em segundo plano para Android 10 (API 29) e superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // Filtra e solicita apenas as permissões que ainda não foram concedidas
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isEmpty()) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                ALL_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /**
     * Lida com o resultado do pedido de permissão.
     *
     * @param requestCode O código do pedido original.
     * @param grantResults Os resultados do pedido.
     * @param onPermissionGranted Callback a ser executado se todas as permissões forem concedidas.
     * @param onPermissionDenied Callback a ser executado se alguma permissão for negada.
     */
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        if (requestCode == ALL_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }
    }
}

/**
 * Este BroadcastReceiver é acionado quando o serviço de localização é encerrado
 * e tenta reiniciá-lo automaticamente.
 */
class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RestartServiceReceiver", "Serviço LocationService recebido. Tentando reiniciar o serviço.")
        context.startService(Intent(context, LocationService::class.java))
    }
}
