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

class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RestartServiceReceiver", "Serviço LocationService recebido. Tentando reiniciar o serviço.")
        context.startService(Intent(context, LocationService::class.java))
    }
}
