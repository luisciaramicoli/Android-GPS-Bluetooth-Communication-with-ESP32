import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class BluetoothGPSService : Service() {

    companion object {
        private const val CHANNEL_ID = "GPS_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("BluetoothGPSService", "Serviço iniciado")

        // Criar o canal de notificação para Android 8.0 ou superior
        createNotificationChannel()

        // Criar uma notificação para rodar em primeiro plano
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Serviço de GPS e Bluetooth")
            .setContentText("Monitorando a localização e conexão Bluetooth")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Ícone padrão do Android
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Serviço de GPS e Bluetooth"
            val descriptionText = "Monitoramento contínuo da localização e do Bluetooth"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BluetoothGPSService", "Serviço rodando em segundo plano")
        // Aqui você pode chamar métodos para coletar GPS e enviar via Bluetooth

        return START_STICKY  // Reinicia o serviço se for interrompido
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BluetoothGPSService", "Serviço encerrado")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
