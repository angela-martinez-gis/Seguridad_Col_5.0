package co.seguridad.agentellamadas.ui

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.seguridad.agentellamadas.real.EstadoReal

/** Pantalla de llamada básica: llamadas que maneja el usuario (salientes, o entrantes sin asistente). */
@Composable
fun EnLlamadaPantalla(
    estado: EstadoReal,
    alContestar: () -> Unit,
    alRechazar: () -> Unit,
    alColgar: () -> Unit,
    alAltavoz: () -> Unit,
    alSilencio: () -> Unit,
    alTeclear: (Char) -> Unit,
) {
    var teclado by remember { mutableStateOf(false) }
    val sonando = estado.entrante && estado.estadoCall == Call.STATE_RINGING
    val texto = when (estado.estadoCall) {
        Call.STATE_RINGING -> "Llamada entrante"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Llamando…"
        Call.STATE_ACTIVE -> "%02d:%02d".format(estado.segundos / 60, estado.segundos % 60)
        Call.STATE_HOLDING -> "En espera"
        Call.STATE_DISCONNECTED -> "Llamada finalizada"
        else -> ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14324F), Colores.fondo)))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(Colores.superficieAlta), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null, tint = Colores.textoSuave, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(estado.numero, color = Colores.texto, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(texto, color = Colores.textoSuave, fontSize = 18.sp)

        Spacer(Modifier.weight(1f))
        if (teclado && !sonando) Teclado(alTeclear)
        Spacer(Modifier.height(24.dp))

        if (sonando) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotonRedondo(Icons.Filled.CallEnd, "Rechazar", Colores.rojo, alRechazar)
                BotonRedondo(Icons.Filled.Call, "Contestar", Colores.verde, alContestar)
            }
        } else if (estado.enCurso) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Alternar(Icons.AutoMirrored.Filled.VolumeUp, "Altavoz", estado.altavoz, alAltavoz)
                Alternar(Icons.Filled.MicOff, "Silencio", estado.silencio, alSilencio)
                Alternar(Icons.Filled.Dialpad, "Teclado", teclado) { teclado = !teclado }
            }
            Spacer(Modifier.height(24.dp))
            BotonRedondo(Icons.Filled.CallEnd, "Colgar", Colores.rojo, alColgar)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Alternar(icono: ImageVector, etiqueta: String, activo: Boolean, alTocar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (activo) Colores.azul else Colores.superficieAlta)
                .clickable(onClick = alTocar),
            contentAlignment = Alignment.Center,
        ) { Icon(icono, etiqueta, tint = Colores.texto) }
        Spacer(Modifier.height(6.dp))
        Text(etiqueta, color = Colores.textoSuave, fontSize = 13.sp)
    }
}

@Composable
private fun Teclado(alTeclear: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (fila in listOf("123", "456", "789", "*0#")) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (d in fila) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Colores.superficie).clickable { alTeclear(d) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$d", color = Colores.texto, fontSize = 24.sp) }
                }
            }
        }
    }
}
