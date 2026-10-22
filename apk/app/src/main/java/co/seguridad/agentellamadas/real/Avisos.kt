package co.seguridad.agentellamadas.real

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import co.seguridad.agentellamadas.MainActivity
import co.seguridad.agentellamadas.R

/**
 * Llamada entrante con la app en segundo plano o la pantalla bloqueada: Android exige una
 * notificación de pantalla completa para mostrar la interfaz de llamada.
 */
object Avisos {

    private const val CANAL = "llamadas"
    private const val ID = 1

    fun mostrarEntrante(context: Context, numero: String, conAsistente: Boolean) {
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // el timbre lo pone el sistema
            },
        )
        val abrir = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val aviso = Notification.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Llamada entrante: $numero")
            .setContentText(if (conAsistente) "El asistente antifraude la va a contestar" else "Toca para contestar")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            .build()
        runCatching { gestor.notify(ID, aviso) }
        // Si la app está en primer plano (o el sistema lo permite), se abre directamente.
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }

    fun quitar(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
