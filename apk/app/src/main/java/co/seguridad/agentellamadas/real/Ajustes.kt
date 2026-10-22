package co.seguridad.agentellamadas.real

import android.content.Context

/** Preferencias del modo de llamadas reales. */
class Ajustes(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("llamadas_reales", Context.MODE_PRIVATE)

    /** Si está apagado, las llamadas entrantes suenan y las contesta el usuario, sin el asistente. */
    var asistenteActivo: Boolean
        get() = prefs.getBoolean(ASISTENTE, true)
        set(valor) = prefs.edit().putBoolean(ASISTENTE, valor).apply()

    /** Diagnóstico: por dónde sale la voz del asistente durante la llamada. */
    var vozPorCanalDeLlamada: Boolean
        get() = prefs.getBoolean(CANAL, true)
        set(valor) = prefs.edit().putBoolean(CANAL, valor).apply()

    private companion object {
        const val ASISTENTE = "asistente_activo"
        const val CANAL = "voz_canal_llamada"
    }
}
