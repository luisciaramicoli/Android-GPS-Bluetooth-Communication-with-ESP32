import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class BluetoothGPSService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("BluetoothGPSService", "Serviço iniciado")
        
        // Criar uma notificação para rodar em primeiro plano
        val notification = NotificationCompat.Builder(this, "GPS_CHANNEL")
            .setContentTitle("Serviço de GPS e Bluetooth")
            .setContentText("Monitorando a localização e conexão Bluetooth")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(1, notification)
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
