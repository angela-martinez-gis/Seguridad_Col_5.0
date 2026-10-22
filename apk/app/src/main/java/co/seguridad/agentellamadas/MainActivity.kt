package co.seguridad.agentellamadas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.seguridad.agentellamadas.datos.Escenarios
import co.seguridad.agentellamadas.llamada.Fase
import co.seguridad.agentellamadas.llamada.LlamadaViewModel
import co.seguridad.agentellamadas.ui.ConversacionPantalla
import co.seguridad.agentellamadas.ui.EntrantePantalla
import co.seguridad.agentellamadas.ui.InicioPantalla
import co.seguridad.agentellamadas.ui.TemaAgente

class MainActivity : ComponentActivity() {

    private val llamada: LlamadaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La demo se proyecta: la pantalla no debe apagarse durante la llamada.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Lanzar un escenario desde la computadora: adb shell am start -n <paquete>/.MainActivity --es escenario spam-robot
        if (savedInstanceState == null) {
            intent.getStringExtra("escenario")?.let { id ->
                Escenarios.todos.firstOrNull { it.id == id }?.let { llamada.simular(it, enVivo = false, conRetraso = false) }
            }
        }

        setContent {
            TemaAgente {
                val estado by llamada.estado.collectAsStateWithLifecycle()
                var enVivo by rememberSaveable { mutableStateOf(false) }
                var conRetraso by rememberSaveable { mutableStateOf(false) }
                val permisoMicrofono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
                    enVivo = concedido
                    if (!concedido) aviso("Sin micrófono se usa el guion automático")
                }

                BackHandler(enabled = estado.fase != Fase.INICIO) { llamada.volverAlInicio() }

                when (estado.fase) {
                    Fase.INICIO, Fase.PROGRAMADA -> InicioPantalla(
                        estado = estado,
                        enVivo = enVivo,
                        conRetraso = conRetraso,
                        alCambiarModo = { quiere ->
                            when {
                                !quiere -> enVivo = false
                                !llamada.reconocimientoDisponible -> aviso("Este teléfono no tiene reconocimiento de voz")
                                tieneMicrofono() -> enVivo = true
                                else -> permisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        alCambiarRetraso = { conRetraso = it },
                        alElegir = { llamada.simular(it, enVivo, conRetraso) },
                        alAleatoria = { llamada.simularAleatoria(enVivo, conRetraso) },
                        alCancelar = llamada::volverAlInicio,
                    )
                    Fase.SONANDO -> EntrantePantalla(
                        estado = estado,
                        alRechazar = llamada::rechazar,
                        alContestar = llamada::contestarYo,
                        alAsistente = llamada::dejarAlAsistente,
                    )
                    Fase.CONVERSANDO, Fase.FINALIZADA -> ConversacionPantalla(
                        estado = estado,
                        alColgar = llamada::colgar,
                        alForzar = llamada::forzar,
                        alReportar = {
                            llamada.reportar()
                            aviso("Número reportado a la red de alertas (simulado)")
                        },
                        alVolver = llamada::volverAlInicio,
                    )
                }
            }
        }
    }

    private fun tieneMicrofono() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
}
