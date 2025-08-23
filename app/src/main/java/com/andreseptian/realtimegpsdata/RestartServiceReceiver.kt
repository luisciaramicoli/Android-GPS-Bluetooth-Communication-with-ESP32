package com.andreseptian.realtimegpsdata // Mude para o seu pacote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Este BroadcastReceiver é acionado para reiniciar o serviço de localização
 * caso ele seja encerrado pelo sistema operacional.
 */
class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RestartServiceReceiver", "Broadcast recebido. Tentando reiniciar o serviço.")

        // CORREÇÃO: O nome do serviço foi ajustado para LocationBluetoothService
        // para corresponder ao arquivo de serviço que criamos.
        val serviceIntent = Intent(context, LocationBluetoothService::class.java)

        // Para Android 8 (Oreo) e superior, é necessário usar startForegroundService.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
