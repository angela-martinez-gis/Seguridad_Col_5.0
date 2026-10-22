package co.seguridad.agentellamadas.llamada

import android.app.Application
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.seguridad.agentellamadas.datos.Escenario
import co.seguridad.agentellamadas.datos.Escenarios
import co.seguridad.agentellamadas.motor.Categoria
import co.seguridad.agentellamadas.motor.Clasificacion
import co.seguridad.agentellamadas.motor.MotorReglas
import co.seguridad.agentellamadas.motor.Tipo
import co.seguridad.agentellamadas.voz.Escucha
import co.seguridad.agentellamadas.voz.Voz
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Fase { INICIO, PROGRAMADA, SONANDO, CONVERSANDO, FINALIZADA }

enum class Actividad(val texto: String) {
    NINGUNA(""),
    CONTESTANDO("Asistente contestando…"),
    HABLANDO("Asistente hablando…"),
    ESCUCHANDO("Escuchando al emisor…"),
    ANALIZANDO("Analizando la respuesta…"),
}

/** Cómo terminó la llamada, para el panel de resultado. */
enum class Desenlace { COLGADA_FRAUDE, COLGADA_SPAM, TRANSFERIDA, TOMADA_POR_USUARIO, RECHAZADA, CORTADA }

data class Mensaje(val deAgente: Boolean, val texto: String)

data class EstadoLlamada(
    val fase: Fase = Fase.INICIO,
    val escenario: Escenario? = null,
    val enVivo: Boolean = false,
    val cuentaRegresiva: Int = 0,
    val actividad: Actividad = Actividad.NINGUNA,
    val mensajes: List<Mensaje> = emptyList(),
    val subtitulo: String? = null,
    val analisis: Clasificacion? = null,
    val veredicto: Clasificacion? = null,
    val desenlace: Desenlace? = null,
    val segundos: Int = 0,
    val forzado: Categoria? = null,
    val reportado: Boolean = false,
)

class LlamadaViewModel(app: Application) : AndroidViewModel(app) {

    private val voz = Voz(app)
    private val escucha = Escucha(app)
    private val vibrador = app.getSystemService(Vibrator::class.java)
    private var timbre: Ringtone? = null
    private var trabajo: Job? = null
    private var cronometro: Job? = null

    private val _estado = MutableStateFlow(EstadoLlamada())
    val estado: StateFlow<EstadoLlamada> = _estado.asStateFlow()

    val reconocimientoDisponible: Boolean get() = escucha.disponible

    init {
        viewModelScope.launch { voz.preparar() }
    }

    fun simular(escenario: Escenario, enVivo: Boolean, conRetraso: Boolean) {
        cancelarTodo()
        _estado.value = EstadoLlamada(escenario = escenario, enVivo = enVivo)
        trabajo = viewModelScope.launch {
            if (conRetraso) {
                for (s in RETRASO_SEGUNDOS downTo 1) {
                    _estado.update { it.copy(fase = Fase.PROGRAMADA, cuentaRegresiva = s) }
                    delay(1000)
                }
            }
            sonar()
        }
    }

    fun simularAleatoria(enVivo: Boolean, conRetraso: Boolean) =
        simular(Escenarios.todos.random(), enVivo, conRetraso)

    /** Durante el timbre: el usuario deja que el asistente conteste ya. */
    fun dejarAlAsistente() {
        if (_estado.value.fase != Fase.SONANDO) return
        trabajo?.cancel()
        trabajo = viewModelScope.launch { atender() }
    }

    fun contestarYo() = terminarDuranteTimbre(Desenlace.TOMADA_POR_USUARIO)

    fun rechazar() = terminarDuranteTimbre(Desenlace.RECHAZADA)

    /** Modo Director: impone el resultado de la siguiente clasificación (y del veredicto). */
    fun forzar(categoria: Categoria?) {
        _estado.update { it.copy(forzado = categoria) }
    }

    fun colgar() {
        if (_estado.value.fase != Fase.CONVERSANDO) return
        cancelarTodo()
        tonoDeCorte()
        _estado.update { it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, subtitulo = null, desenlace = Desenlace.CORTADA) }
    }

    fun reportar() {
        _estado.update { it.copy(reportado = true) }
    }

    fun volverAlInicio() {
        cancelarTodo()
        _estado.value = EstadoLlamada()
    }

    private suspend fun sonar() {
        _estado.update { it.copy(fase = Fase.SONANDO, cuentaRegresiva = TIMBRE_SEGUNDOS) }
        iniciarTimbre()
        for (s in TIMBRE_SEGUNDOS downTo 1) {
            _estado.update { it.copy(cuentaRegresiva = s) }
            delay(1000)
        }
        atender()
    }

    private suspend fun atender() {
        detenerTimbre()
        _estado.update { it.copy(fase = Fase.CONVERSANDO, actividad = Actividad.CONTESTANDO, cuentaRegresiva = 0) }
        cronometro = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _estado.update { it.copy(segundos = it.segundos + 1) }
            }
        }
        delay(800)
        conversar()
    }

    private suspend fun conversar() {
        val escenario = _estado.value.escenario ?: return
        decir(PREGUNTA_1)

        var r1 = turnoEmisor(escenario.respuesta1)
        if (r1 == null) {
            decir(REPETIR)
            r1 = turnoEmisor(escenario.respuesta1)
        }
        val previa = analizar(r1.orEmpty())
        decir(preguntaDos(previa))

        val r2 = turnoEmisor(escenario.respuesta2)
        val final = analizar(listOfNotNull(r1, r2).joinToString(". ")).let {
            // Sin motivo claro al final, el asistente no deja pasar la llamada.
            if (it.categoria == Categoria.INDETERMINADO) it.copy(categoria = Categoria.SPAM, tipo = null, confianza = 50) else it
        }
        _estado.update { it.copy(veredicto = final) }
        alertar(final.categoria)
        decir(despedida(final.categoria))

        val desenlace = when (final.categoria) {
            Categoria.FRAUDE -> Desenlace.COLGADA_FRAUDE
            Categoria.LEGITIMO -> Desenlace.TRANSFERIDA
            else -> Desenlace.COLGADA_SPAM
        }
        if (desenlace != Desenlace.TRANSFERIDA) tonoDeCorte()
        cronometro?.cancel()
        _estado.update { it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, desenlace = desenlace) }
    }

    private suspend fun decir(texto: String) {
        _estado.update { it.copy(actividad = Actividad.HABLANDO, mensajes = it.mensajes + Mensaje(true, texto)) }
        voz.hablar(texto, comoEmisor = false)
        delay(250)
    }

    /** Respuesta del emisor: el guion con otra voz, o lo que diga una persona al micrófono. */
    private suspend fun turnoEmisor(guion: String): String? {
        _estado.update { it.copy(actividad = Actividad.ESCUCHANDO, subtitulo = "") }
        val texto = if (_estado.value.enVivo) {
            escucha.escuchar { parcial -> _estado.update { it.copy(subtitulo = parcial) } }
        } else {
            reproducirGuion(guion)
            guion
        }
        _estado.update {
            it.copy(subtitulo = null, mensajes = if (texto != null) it.mensajes + Mensaje(false, texto) else it.mensajes)
        }
        return texto
    }

    /** Subtítulos palabra a palabra mientras habla la voz del emisor, como una transcripción en vivo. */
    private suspend fun reproducirGuion(guion: String) = coroutineScope {
        val palabras = guion.split(" ")
        val subtitulos = launch {
            for (n in 1..palabras.size) {
                _estado.update { it.copy(subtitulo = palabras.take(n).joinToString(" ")) }
                delay(MS_POR_PALABRA)
            }
        }
        voz.hablar(guion, comoEmisor = true)
        subtitulos.cancel()
    }

    private suspend fun analizar(texto: String): Clasificacion {
        _estado.update { it.copy(actividad = Actividad.ANALIZANDO) }
        delay(900)
        val base = MotorReglas.clasificar(texto)
        val resultado = _estado.value.forzado?.let { MotorReglas.forzar(base, it) } ?: base
        _estado.update { it.copy(analisis = resultado) }
        return resultado
    }

    private fun preguntaDos(c: Clasificacion): String = when (c.categoria) {
        Categoria.FRAUDE -> when (c.tipo) {
            Tipo.BANCARIO -> "Por seguridad, ¿me indica su nombre completo, su código de asesor y desde qué sede llama?"
            Tipo.FAMILIAR -> "Entiendo. ¿Me dice su nombre completo y qué parentesco tiene con ${Escenarios.TITULAR}?"
            Tipo.PREMIO -> "¿Cuál es el nombre de la empresa y cómo se puede verificar ese sorteo?"
            Tipo.AUTORIDAD -> "¿Me indica su nombre, su cargo y el número del proceso para verificarlo en los canales oficiales?"
            Tipo.SOPORTE -> "¿Desde qué área llama y cuál es el número del caso?"
            else -> "¿De qué entidad llama y cómo puedo verificar su identidad?"
        }
        Categoria.SPAM -> when (c.tipo) {
            Tipo.ROBOT -> "¿Hay una persona en la línea? Por favor, dígame su nombre."
            else -> "¿Podría eliminar este número de su base de datos comercial?"
        }
        Categoria.LEGITIMO -> "¿Es un asunto urgente o prefiere que le deje una nota a ${Escenarios.TITULAR}?"
        Categoria.INDETERMINADO -> "¿Podría darme más detalles? ¿De parte de quién y de qué se trata?"
    }

    private fun despedida(categoria: Categoria): String = when (categoria) {
        Categoria.FRAUDE -> "Esta llamada no puede continuar. Por seguridad, no se entregará ninguna información. Hasta luego."
        Categoria.LEGITIMO -> "Gracias. Ya le comunico con ${Escenarios.TITULAR}, un momento por favor."
        else -> "Gracias por llamar. ${Escenarios.TITULAR} no está interesada; por favor retire este número de su lista. Que tenga buen día."
    }

    private fun terminarDuranteTimbre(desenlace: Desenlace) {
        if (_estado.value.fase != Fase.SONANDO) return
        cancelarTodo()
        _estado.update { it.copy(fase = Fase.FINALIZADA, desenlace = desenlace, cuentaRegresiva = 0) }
    }

    private fun iniciarTimbre() {
        val app = getApplication<Application>()
        timbre = runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }.getOrNull()?.also { runCatching { it.play() } }
        vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun detenerTimbre() {
        runCatching { timbre?.stop() }
        timbre = null
        vibrador?.cancel()
    }

    private fun alertar(categoria: Categoria) {
        val patron = when (categoria) {
            Categoria.FRAUDE -> longArrayOf(0, 300, 120, 300, 120, 600)
            Categoria.LEGITIMO -> longArrayOf(0, 120)
            else -> longArrayOf(0, 250)
        }
        vibrador?.vibrate(VibrationEffect.createWaveform(patron, -1))
    }

    private fun tonoDeCorte() {
        viewModelScope.launch {
            runCatching {
                val tono = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tono.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
                delay(500)
                tono.release()
            }
        }
    }

    private fun cancelarTodo() {
        trabajo?.cancel()
        cronometro?.cancel()
        trabajo = null
        cronometro = null
        detenerTimbre()
        voz.detener()
    }

    override fun onCleared() {
        cancelarTodo()
        voz.liberar()
    }

    companion object {
        const val RETRASO_SEGUNDOS = 5
        const val TIMBRE_SEGUNDOS = 4
        private const val MS_POR_PALABRA = 260L

        const val PREGUNTA_1 = "Hola, habla el asistente de seguridad de ${Escenarios.TITULAR}. ¿Cuál es el motivo de su llamada?"
        const val REPETIR = "Disculpe, no le escuché con claridad. ¿Podría repetir el motivo de su llamada?"
    }
}
