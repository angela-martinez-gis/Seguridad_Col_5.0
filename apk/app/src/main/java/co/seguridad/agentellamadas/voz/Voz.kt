package co.seguridad.agentellamadas.voz

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Síntesis de voz con dos "personas": el asistente y el emisor simulado.
 * Si el teléfono no tiene voz en español, la demo continúa en silencio con los subtítulos.
 */
class Voz(context: Context) {

    private val listo = CompletableDeferred<Boolean>()
    private val pendientes = ConcurrentHashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext) { estado ->
        listo.complete(estado == TextToSpeech.SUCCESS)
    }
    private var vozAgente: Voice? = null
    private var vozEmisor: Voice? = null
    private var configurada = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) { pendientes.remove(id)?.invoke() }
            @Deprecated("Requerido por la API")
            override fun onError(id: String) { pendientes.remove(id)?.invoke() }
            override fun onError(id: String, codigo: Int) { pendientes.remove(id)?.invoke() }
            override fun onStop(id: String, interrumpida: Boolean) { pendientes.remove(id)?.invoke() }
        })
    }

    /** `true` si hay voz en español disponible. */
    suspend fun preparar(): Boolean {
        if (!listo.await()) return false
        if (configurada) return true
        // Acento latinoamericano primero: "es-CO" suele resolverse a la voz de España si no existe.
        val idioma = PAISES.map { Locale("es", it) }
            .firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_COUNTRY_AVAILABLE }
            ?: Locale("es").takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: return false
        tts.language = idioma
        val voces = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "es" && !it.isNetworkConnectionRequired && "notinstalled" !in it.features }
            .sortedWith(compareBy({ PAISES.indexOf(it.locale.country).let { i -> if (i < 0) PAISES.size else i } }, { it.name }))
        vozAgente = voces.firstOrNull()
        // Preferimos otra voz del mismo país para el emisor; el tono la diferencia si no hay más.
        vozEmisor = voces.firstOrNull { it.name != vozAgente?.name && it.locale.country == vozAgente?.locale?.country }
            ?: voces.firstOrNull { it.name != vozAgente?.name }
        configurada = true
        return true
    }

    suspend fun hablar(texto: String, comoEmisor: Boolean) {
        if (!preparar()) {
            delay(texto.length * 55L) // sin voz: se respeta el ritmo de los subtítulos
            return
        }
        (if (comoEmisor) vozEmisor else vozAgente)?.let { tts.voice = it }
        tts.setPitch(if (comoEmisor && vozEmisor == null) 0.8f else 1.0f)
        tts.setSpeechRate(if (comoEmisor) 1.15f else 1.1f)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            pendientes[id] = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation {
                pendientes.remove(id)
                tts.stop()
            }
            if (tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                pendientes.remove(id)
                cont.resume(Unit)
            }
        }
    }

    /**
     * En una llamada real el asistente habla por el altavoz para que el micrófono lo transmita.
     * `true` usa el volumen de la llamada; `false`, el de asistente (diagnóstico por teléfono).
     */
    fun usarCanalDeLlamada(canalDeLlamada: Boolean) {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (canalDeLlamada) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    fun detener() {
        tts.stop()
    }

    fun liberar() {
        tts.shutdown()
    }

    private companion object {
        val PAISES = listOf("CO", "US", "MX", "419", "AR", "ES")
    }
}
