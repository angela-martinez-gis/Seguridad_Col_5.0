package co.seguridad.agentellamadas.real

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * El sistema lo enlaza cuando la app es el teléfono predeterminado: recibe todas las
 * llamadas (entrantes y salientes) y controla su audio. La lógica vive en [GestorLlamadas].
 */
class ServicioLlamadas : InCallService() {

    override fun onCreate() {
        super.onCreate()
        GestorLlamadas.servicio = this
    }

    override fun onDestroy() {
        GestorLlamadas.servicio = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) = GestorLlamadas.agregada(this, call)

    override fun onCallRemoved(call: Call) = GestorLlamadas.removida(this, call)

    @Deprecated("Requerido mientras minSdk < 34")
    override fun onCallAudioStateChanged(estado: CallAudioState) = GestorLlamadas.audioCambiado(estado)

    /** Altavoz encendido o auricular. */
    @Suppress("DEPRECATION") // setAudioRoute sigue funcionando; requestCallEndpointChange es solo API 34+.
    fun altavoz(encendido: Boolean) {
        setAudioRoute(if (encendido) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }
}
