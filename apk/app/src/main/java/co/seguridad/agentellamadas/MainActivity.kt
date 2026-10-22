package co.seguridad.agentellamadas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.seguridad.agentellamadas.datos.Escenarios
import co.seguridad.agentellamadas.llamada.Desenlace
import co.seguridad.agentellamadas.llamada.Fase
import co.seguridad.agentellamadas.llamada.LlamadaViewModel
import co.seguridad.agentellamadas.real.Ajustes
import co.seguridad.agentellamadas.real.EstadoReal
import co.seguridad.agentellamadas.real.GestorLlamadas
import co.seguridad.agentellamadas.real.ModoReal
import co.seguridad.agentellamadas.ui.ConversacionPantalla
import co.seguridad.agentellamadas.ui.EnLlamadaPantalla
import co.seguridad.agentellamadas.ui.EntrantePantalla
import co.seguridad.agentellamadas.ui.InicioPantalla
import co.seguridad.agentellamadas.ui.LlamadasRealesPantalla
import co.seguridad.agentellamadas.ui.TemaAgente

class MainActivity : ComponentActivity() {

    private val llamada: LlamadaViewModel by viewModels()

    /** Se incrementa al volver a la app para releer rol y permisos. */
    private var revision by mutableIntStateOf(0)

    /** Número recibido por ACTION_DIAL (otra app pidió marcar). */
    private var numeroPorMarcar by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La demo se proyecta: la pantalla no debe apagarse durante la llamada.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) atenderIntent(intent)

        setContent {
            TemaAgente {
                val real by GestorLlamadas.estado.collectAsStateWithLifecycle()
                if (real.modo != ModoReal.NINGUNA) {
                    LlamadaReal(real)
                } else {
                    Simulacion()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atenderIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        revision++
    }

    private fun atenderIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> numeroPorMarcar = intent.data?.schemeSpecificPart.orEmpty()
            else -> {
                // Lanzar un escenario desde la computadora: adb shell am start -n <paquete>/.MainActivity --es escenario spam-robot
                intent?.getStringExtra("escenario")?.let { id ->
                    Escenarios.todos.firstOrNull { it.id == id }?.let { llamada.simular(it, enVivo = false, conRetraso = false) }
                }
            }
        }
    }

    @Composable
    private fun LlamadaReal(real: EstadoReal) {
        BackHandler { /* la llamada se termina con sus botones */ }
        val a = real.asistente
        when {
            real.modo == ModoReal.USUARIO -> EnLlamadaPantalla(
                estado = real,
                alContestar = { GestorLlamadas.contestarYo(this) },
                alRechazar = GestorLlamadas::rechazar,
                alColgar = GestorLlamadas::colgar,
                alAltavoz = GestorLlamadas::alternarAltavoz,
                alSilencio = GestorLlamadas::alternarSilencio,
                alTeclear = GestorLlamadas::teclear,
            )
            a.fase == Fase.SONANDO -> EntrantePantalla(
                estado = a,
                alRechazar = GestorLlamadas::rechazar,
                alContestar = { GestorLlamadas.contestarYo(this) },
                alAsistente = { GestorLlamadas.dejarAlAsistente(this) },
            )
            else -> ConversacionPantalla(
                estado = a,
                alColgar = GestorLlamadas::cortarAsistente,
                alForzar = GestorLlamadas::forzar,
                alReportar = {
                    GestorLlamadas.reportar()
                    aviso("Número reportado a la red de alertas (simulado)")
                },
                alVolver = GestorLlamadas::cerrarResultado,
                textoVolver = if (a.desenlace == Desenlace.TRANSFERIDA && real.enCurso) "Tomar la llamada" else "Cerrar",
            )
        }
    }

    @Composable
    private fun Simulacion() {
        val estado by llamada.estado.collectAsStateWithLifecycle()
        var enVivo by rememberSaveable { mutableStateOf(false) }
        var conRetraso by rememberSaveable { mutableStateOf(false) }
        var enReales by rememberSaveable { mutableStateOf(false) }
        val permisoMicrofono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            enVivo = concedido
            if (!concedido) aviso("Sin micrófono se usa el guion automático")
        }
        LaunchedEffect(numeroPorMarcar) { if (numeroPorMarcar != null) enReales = true }

        BackHandler(enabled = estado.fase != Fase.INICIO || enReales) {
            if (enReales) {
                enReales = false
                numeroPorMarcar = null
            } else {
                llamada.volverAlInicio()
            }
        }

        when {
            enReales -> Reales(alVolver = {
                enReales = false
                numeroPorMarcar = null
            })
            estado.fase == Fase.INICIO || estado.fase == Fase.PROGRAMADA -> InicioPantalla(
                estado = estado,
                enVivo = enVivo,
                conRetraso = conRetraso,
                alCambiarModo = { quiere ->
                    when {
                        !quiere -> enVivo = false
                        !llamada.reconocimientoDisponible -> aviso("Este teléfono no tiene reconocimiento de voz")
                        tienePermiso(Manifest.permission.RECORD_AUDIO) -> enVivo = true
                        else -> permisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                alCambiarRetraso = { conRetraso = it },
                alElegir = { llamada.simular(it, enVivo, conRetraso) },
                alAleatoria = { llamada.simularAleatoria(enVivo, conRetraso) },
                alCancelar = llamada::volverAlInicio,
                alAbrirReales = { enReales = true },
            )
            estado.fase == Fase.SONANDO -> EntrantePantalla(
                estado = estado,
                alRechazar = llamada::rechazar,
                alContestar = llamada::contestarYo,
                alAsistente = llamada::dejarAlAsistente,
            )
            else -> ConversacionPantalla(
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

    @Composable
    private fun Reales(alVolver: () -> Unit) {
        val ajustes = remember { Ajustes(this) }
        var asistente by remember { mutableStateOf(ajustes.asistenteActivo) }
        var vozPorLlamada by remember { mutableStateOf(ajustes.vozPorCanalDeLlamada) }
        var numero by rememberSaveable { mutableStateOf(numeroPorMarcar.orEmpty()) }
        val rol = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
        val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }

        // `revision` cambia al volver de Ajustes o de un diálogo: se releen rol y permisos.
        val predeterminada = remember(revision) { GestorLlamadas.esTelefonoPredeterminado(this) }
        val completos = remember(revision) { permisosNecesarios.all(::tienePermiso) }

        LlamadasRealesPantalla(
            esPredeterminada = predeterminada,
            permisosCompletos = completos,
            asistenteActivo = asistente,
            vozPorLlamada = vozPorLlamada,
            numero = numero,
            alCambiarNumero = { numero = it },
            alHacerPredeterminada = { rol.launch(GestorLlamadas.solicitudDeRol(this)) },
            alPedirPermisos = { permisos.launch(permisosNecesarios.toTypedArray()) },
            alCambiarAsistente = {
                asistente = it
                ajustes.asistenteActivo = it
            },
            alCambiarVoz = {
                vozPorLlamada = it
                ajustes.vozPorCanalDeLlamada = it
            },
            alLlamar = {
                if (!tienePermiso(Manifest.permission.CALL_PHONE)) {
                    permisos.launch(permisosNecesarios.toTypedArray())
                } else if (!GestorLlamadas.llamar(this, numero)) {
                    aviso("No se pudo iniciar la llamada")
                }
            },
            alRestaurarTelefono = {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                    .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                aviso("Elige tu app de Teléfono original en \"App de teléfono\"")
            },
            alVolver = alVolver,
        )
    }

    private val permisosNecesarios: List<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }

    private fun tienePermiso(permiso: String) =
        ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED

    private fun aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
}
