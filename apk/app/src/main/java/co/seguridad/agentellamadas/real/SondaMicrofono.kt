package co.seguridad.agentellamadas.real

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Diagnóstico durante una llamada: mide cuánto sonido llega a la app por cada fuente de audio.
 * Si todas dan ~0 %, Android le está entregando silencio a la app mientras dura la llamada.
 */
object SondaMicrofono {

    private const val MUESTREO = 16_000
    private const val MS_POR_FUENTE = 1500

    private val fuentes = listOf(
        "MIC" to MediaRecorder.AudioSource.MIC,
        "RECONOCIMIENTO" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "COMUNICACION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "CAMARA" to MediaRecorder.AudioSource.CAMCORDER,
        "SIN_PROCESAR" to MediaRecorder.AudioSource.UNPROCESSED,
    )

    /** Pico de nivel (0–100 %) por fuente; `null` si la fuente no se pudo abrir. */
    @SuppressLint("MissingPermission") // Se llama solo con RECORD_AUDIO concedido (requisito del modo real).
    suspend fun medir(): List<Pair<String, Int?>> = withContext(Dispatchers.IO) {
        val minimo = AudioRecord.getMinBufferSize(MUESTREO, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        fuentes.map { (nombre, fuente) ->
            nombre to runCatching {
                val grabadora = AudioRecord(fuente, MUESTREO, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minimo * 4)
                try {
                    check(grabadora.state == AudioRecord.STATE_INITIALIZED)
                    grabadora.startRecording()
                    val bufer = ShortArray(minimo)
                    var pico = 0
                    var leidas = 0
                    val total = MUESTREO * MS_POR_FUENTE / 1000
                    while (leidas < total) {
                        val n = grabadora.read(bufer, 0, bufer.size)
                        if (n <= 0) break
                        for (i in 0 until n) pico = maxOf(pico, abs(bufer[i].toInt()))
                        leidas += n
                    }
                    pico * 100 / Short.MAX_VALUE
                } finally {
                    grabadora.release()
                }
            }.getOrNull()
        }
    }

    fun resumen(niveles: List<Pair<String, Int?>>): String =
        niveles.joinToString(" · ") { (nombre, nivel) -> "$nombre ${nivel?.let { "$it %" } ?: "bloqueado"}" }
}
