package co.seguridad.agentellamadas.voz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reconocimiento de voz del sistema para el modo en vivo: una persona hace de emisor
 * y habla cerca del micrófono del teléfono. Debe llamarse desde el hilo principal.
 */
class Escucha(context: Context) {

    private val contexto = context.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    val disponible: Boolean get() = SpeechRecognizer.isRecognitionAvailable(contexto)

    /** Por qué falló la última escucha (diagnóstico), o `null` si entendió algo. */
    var ultimoFallo: String? = null
        private set

    /** Devuelve lo que se entendió, o `null` si hubo silencio, error o pasó el tiempo límite. */
    suspend fun escuchar(alParcial: (String) -> Unit): String? {
        ultimoFallo = null
        val texto = withTimeoutOrNull(LIMITE_MS) { reconocer(alParcial) }
        if (texto == null && ultimoFallo == null) ultimoFallo = "tiempo agotado sin respuesta del reconocedor"
        return texto
    }

    private suspend fun reconocer(alParcial: (String) -> Unit): String? =
        suspendCancellableCoroutine { cont ->
            val reconocedor = SpeechRecognizer.createSpeechRecognizer(contexto)
            fun terminar(texto: String?) {
                reconocedor.destroy()
                if (texto.isNullOrBlank() && ultimoFallo == null) ultimoFallo = "no llegó texto"
                if (cont.isActive) cont.resume(texto?.takeIf { it.isNotBlank() })
            }
            reconocedor.setRecognitionListener(object : RecognitionListener {
                override fun onResults(resultados: Bundle) =
                    terminar(resultados.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())

                override fun onPartialResults(parciales: Bundle) {
                    parciales.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }?.let(alParcial)
                }

                override fun onError(error: Int) {
                    ultimoFallo = describir(error)
                    terminar(null)
                }
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rms: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(tipo: Int, params: Bundle?) = Unit
            })
            cont.invokeOnCancellation { principal.post { reconocedor.destroy() } }
            reconocedor.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L),
            )
        }

    private fun describir(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "el sistema no entregó audio del micrófono"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "solo llegó silencio"
        SpeechRecognizer.ERROR_NO_MATCH -> "llegó sonido pero no se reconocieron palabras"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "falta el permiso de micrófono"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "sin internet para el reconocedor"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "reconocedor ocupado"
        SpeechRecognizer.ERROR_CLIENT -> "error del cliente de reconocimiento"
        else -> "error del reconocedor ($error)"
    }

    private companion object {
        const val LIMITE_MS = 15_000L
    }
}
