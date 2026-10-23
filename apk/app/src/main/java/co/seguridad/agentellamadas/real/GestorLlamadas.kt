package co.seguridad.agentellamadas.real

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Log
import android.os.Vibrator
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import co.seguridad.agentellamadas.MainActivity
import co.seguridad.agentellamadas.llamada.Actividad
import co.seguridad.agentellamadas.llamada.Desenlace
import co.seguridad.agentellamadas.llamada.EstadoLlamada
import co.seguridad.agentellamadas.llamada.Fase
import co.seguridad.agentellamadas.llamada.Mensaje
import co.seguridad.agentellamadas.motor.Categoria
import co.seguridad.agentellamadas.motor.Clasificacion
import co.seguridad.agentellamadas.motor.Guion
import co.seguridad.agentellamadas.motor.MotorReglas
import co.seguridad.agentellamadas.voz.Escucha
import co.seguridad.agentellamadas.voz.Voz
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Quién maneja la llamada real en curso. */
enum class ModoReal { NINGUNA, ASISTENTE, USUARIO }

data class EstadoReal(
    val modo: ModoReal = ModoReal.NINGUNA,
    /** Misma forma que la simulación, para reutilizar sus pantallas. */
    val asistente: EstadoLlamada = EstadoLlamada(),
    val numero: String = "",
    val entrante: Boolean = false,
    val estadoCall: Int = Call.STATE_NEW,
    val altavoz: Boolean = false,
    val silencio: Boolean = false,
    val segundos: Int = 0,
) {
    val enCurso: Boolean get() = estadoCall != Call.STATE_DISCONNECTED
}

/**
 * Llamadas SIM reales cuando la app es el teléfono predeterminado. El asistente contesta
 * con el altavoz activado: su voz sale por el parlante y el micrófono la transmite, y el
 * reconocimiento de voz escucha al emisor por el mismo altavoz. Una llamada a la vez.
 */
object GestorLlamadas {

    private const val TIMBRE_SEGUNDOS = 4
    private const val ETIQUETA = "AgenteLlamadas"

    var servicio: ServicioLlamadas? = null

    private val alcance = MainScope()
    private val _estado = MutableStateFlow(EstadoReal())
    val estado: StateFlow<EstadoReal> = _estado.asStateFlow()

    private var llamada: Call? = null
    private var trabajo: Job? = null
    private var cronometro: Job? = null
    private var voz: Voz? = null
    private var escucha: Escucha? = null

    /** Resultado de [SondaMicrofono] en la llamada actual (se mide una sola vez). */
    private var diagnostico: String? = null

    private val alCambiar = object : Call.Callback() {
        override fun onStateChanged(call: Call, estado: Int) = estadoCambiado(estado)
    }

    // ── Eventos del sistema (ServicioLlamadas)

    fun agregada(context: Context, call: Call) {
        val entrante = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        if (llamada != null) {
            // PoC: una sola llamada. Una segunda entrante se rechaza para no dejarla sonando sin pantalla.
            if (entrante) call.reject(false, null)
            return
        }
        llamada = call
        diagnostico = null
        call.registerCallback(alCambiar)
        val numero = call.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: "Número privado"
        val conAsistente = entrante && Ajustes(context).asistenteActivo
        _estado.value = EstadoReal(
            modo = if (conAsistente) ModoReal.ASISTENTE else ModoReal.USUARIO,
            numero = numero,
            entrante = entrante,
            estadoCall = call.estadoActual,
            asistente = EstadoLlamada(
                fase = Fase.SONANDO,
                numero = numero,
                ubicacion = "Llamada real · número sin verificar",
                enVivo = true,
                cuentaRegresiva = TIMBRE_SEGUNDOS,
            ),
        )
        if (entrante) Avisos.mostrarEntrante(context, numero, conAsistente) else abrirPantalla(context)
        if (conAsistente) {
            val app = context.applicationContext
            trabajo = alcance.launch {
                for (s in TIMBRE_SEGUNDOS downTo 1) {
                    actualizarAsistente { it.copy(cuentaRegresiva = s) }
                    delay(1000)
                }
                atender(app)
            }
        }
    }

    fun removida(context: Context, call: Call) {
        call.unregisterCallback(alCambiar)
        if (call != llamada) return
        llamada = null
        cronometro?.cancel()
        Avisos.quitar(context)
        val actual = _estado.value
        // El resultado del asistente queda en pantalla hasta que el usuario lo cierre.
        if (actual.modo != ModoReal.ASISTENTE) {
            alcance.launch {
                delay(1500)
                if (llamada == null) _estado.value = EstadoReal()
            }
        }
    }

    fun audioCambiado(audio: CallAudioState) {
        _estado.update { it.copy(altavoz = audio.route == CallAudioState.ROUTE_SPEAKER, silencio = audio.isMuted) }
    }

    private fun estadoCambiado(estado: Int) {
        _estado.update { it.copy(estadoCall = estado) }
        if (estado == Call.STATE_ACTIVE && cronometro?.isActive != true) iniciarCronometro()
        if (estado == Call.STATE_DISCONNECTED) {
            val fase = _estado.value.asistente.fase
            if (_estado.value.modo == ModoReal.ASISTENTE && (fase == Fase.SONANDO || fase == Fase.CONVERSANDO)) {
                // Cortaron antes del veredicto: frecuente en fraudes y robots al escuchar al asistente.
                detenerAsistente()
                actualizarAsistente {
                    it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, subtitulo = null, desenlace = Desenlace.COLGO_EMISOR)
                }
            }
        }
    }

    // ── Acciones de la interfaz

    fun dejarAlAsistente(context: Context) {
        if (_estado.value.asistente.fase != Fase.SONANDO) return
        trabajo?.cancel()
        val app = context.applicationContext
        trabajo = alcance.launch { atender(app) }
    }

    fun contestarYo(context: Context) {
        trabajo?.cancel()
        Avisos.quitar(context)
        llamada?.answer(VideoProfile.STATE_AUDIO_ONLY)
        _estado.update { it.copy(modo = ModoReal.USUARIO) }
    }

    fun rechazar() {
        trabajo?.cancel()
        _estado.update { it.copy(modo = ModoReal.USUARIO) }
        llamada?.reject(false, null)
    }

    /** Colgar mientras el asistente conversa. */
    fun cortarAsistente() {
        detenerAsistente()
        actualizarAsistente { it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, subtitulo = null, desenlace = Desenlace.CORTADA) }
        llamada?.disconnect()
    }

    fun colgar() {
        llamada?.disconnect()
    }

    fun forzar(categoria: Categoria?) = actualizarAsistente { it.copy(forzado = categoria) }

    fun reportar() = actualizarAsistente { it.copy(reportado = true) }

    /** Llamada legítima: el usuario la toma por el auricular. */
    fun tomarLlamada() {
        servicio?.altavoz(false)
        _estado.update { it.copy(modo = ModoReal.USUARIO) }
    }

    /** Cierra el resultado del asistente. */
    fun cerrarResultado() {
        if (llamada == null) _estado.value = EstadoReal() else tomarLlamada()
    }

    fun alternarAltavoz() {
        servicio?.altavoz(!_estado.value.altavoz)
    }

    fun alternarSilencio() {
        servicio?.setMuted(!_estado.value.silencio)
    }

    fun teclear(digito: Char) {
        llamada?.playDtmfTone(digito)
        llamada?.stopDtmfTone()
    }

    // ── Llamar y rol de teléfono

    fun esTelefonoPredeterminado(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    fun solicitudDeRol(context: Context): Intent =
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)

    /** `false` si falta el permiso de llamar. */
    fun llamar(context: Context, numero: String): Boolean = runCatching {
        context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", numero, null), Bundle())
    }.isSuccess

    // ── Conversación del asistente

    private suspend fun atender(context: Context) {
        val call = llamada ?: return
        Avisos.quitar(context)
        actualizarAsistente { it.copy(fase = Fase.CONVERSANDO, actividad = Actividad.CONTESTANDO, cuentaRegresiva = 0) }
        call.answer(VideoProfile.STATE_AUDIO_ONLY)
        withTimeoutOrNull(6000) { estado.first { it.estadoCall == Call.STATE_ACTIVE } }
        servicio?.altavoz(true)
        val v = voz ?: Voz(context).also { voz = it }
        v.usarCanalDeLlamada(Ajustes(context).vozPorCanalDeLlamada)
        if (escucha == null) escucha = Escucha(context)
        delay(1200) // la ruta de audio tarda en pasar al altavoz
        conversar(context, v)
    }

    private suspend fun conversar(context: Context, v: Voz) {
        decir(v, Guion.PREGUNTA_1)
        var r1 = escuchar()
        if (r1 == null) {
            decir(v, Guion.REPETIR)
            r1 = escuchar()
        }
        val previa = analizar(r1.orEmpty())
        decir(v, Guion.preguntaDos(previa))
        val r2 = escuchar()
        val final = Guion.veredictoFinal(analizar(listOfNotNull(r1, r2).joinToString(". ")))
        actualizarAsistente { it.copy(veredicto = final) }
        vibrar(context, final.categoria)
        decir(v, Guion.despedida(final.categoria))

        if (final.categoria == Categoria.LEGITIMO) {
            servicio?.altavoz(false)
            actualizarAsistente { it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, desenlace = Desenlace.TRANSFERIDA) }
        } else {
            val desenlace = if (final.categoria == Categoria.FRAUDE) Desenlace.COLGADA_FRAUDE else Desenlace.COLGADA_SPAM
            actualizarAsistente { it.copy(fase = Fase.FINALIZADA, actividad = Actividad.NINGUNA, desenlace = desenlace) }
            llamada?.disconnect()
        }
    }

    private suspend fun decir(v: Voz, texto: String) {
        actualizarAsistente { it.copy(actividad = Actividad.HABLANDO, mensajes = it.mensajes + Mensaje(true, texto)) }
        v.hablar(texto, comoEmisor = false)
        delay(250)
    }

    private suspend fun escuchar(): String? {
        actualizarAsistente { it.copy(actividad = Actividad.ESCUCHANDO, subtitulo = "") }
        val texto = escucha?.escuchar { parcial -> actualizarAsistente { it.copy(subtitulo = parcial) } }
        actualizarAsistente {
            it.copy(
                subtitulo = null,
                mensajes = if (texto != null) it.mensajes + Mensaje(false, texto) else it.mensajes,
                aviso = if (texto == null) {
                    "No se escuchó al emisor: ${escucha?.ultimoFallo}." +
                        (diagnostico?.let { d -> "\nNivel del micrófono en llamada: $d" } ?: " Si se repite, usa el Modo Director.")
                } else {
                    null
                },
            )
        }
        if (texto == null && diagnostico == null) {
            // Una vez por llamada: ¿llega sonido a la app? Mientras mide, el emisor debe seguir hablando.
            actualizarAsistente { it.copy(actividad = Actividad.ANALIZANDO, aviso = "Midiendo el micrófono… (que el emisor siga hablando)") }
            val resumen = SondaMicrofono.resumen(SondaMicrofono.medir())
            diagnostico = resumen
            Log.i(ETIQUETA, "Fallo de escucha: ${escucha?.ultimoFallo} · niveles: $resumen")
            actualizarAsistente {
                it.copy(aviso = "No se escuchó al emisor: ${escucha?.ultimoFallo}.\nNivel del micrófono en llamada: $resumen")
            }
        }
        return texto
    }

    private suspend fun analizar(texto: String): Clasificacion {
        actualizarAsistente { it.copy(actividad = Actividad.ANALIZANDO) }
        delay(600)
        val base = MotorReglas.clasificar(texto)
        val resultado = _estado.value.asistente.forzado?.let { MotorReglas.forzar(base, it) } ?: base
        actualizarAsistente { it.copy(analisis = resultado) }
        return resultado
    }

    // ── Utilidades

    private fun actualizarAsistente(cambio: (EstadoLlamada) -> EstadoLlamada) =
        _estado.update { it.copy(asistente = cambio(it.asistente)) }

    private fun detenerAsistente() {
        trabajo?.cancel()
        voz?.detener()
    }

    private fun iniciarCronometro() {
        cronometro?.cancel()
        cronometro = alcance.launch {
            while (isActive) {
                delay(1000)
                _estado.update { it.copy(segundos = it.segundos + 1, asistente = it.asistente.copy(segundos = it.segundos + 1)) }
            }
        }
    }

    private fun vibrar(context: Context, categoria: Categoria) {
        val patron = when (categoria) {
            Categoria.FRAUDE -> longArrayOf(0, 300, 120, 300, 120, 600)
            Categoria.LEGITIMO -> longArrayOf(0, 500, 200, 500) // avisa al usuario que tome la llamada
            else -> longArrayOf(0, 250)
        }
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(patron, -1))
    }

    private fun abrirPantalla(context: Context) {
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    @Suppress("DEPRECATION")
    private val Call.estadoActual: Int
        get() = if (Build.VERSION.SDK_INT >= 31) details.state else state
}
