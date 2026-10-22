package co.seguridad.agentellamadas.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.seguridad.agentellamadas.llamada.EstadoLlamada

@Composable
fun EntrantePantalla(
    estado: EstadoLlamada,
    alRechazar: () -> Unit,
    alContestar: () -> Unit,
    alAsistente: () -> Unit,
) {
    val pulso = rememberInfiniteTransition(label = "pulso")
    val escala by pulso.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "escala")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14324F), Colores.fondo)))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Llamada entrante", color = Colores.textoSuave, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.size(112.dp).scale(escala).clip(CircleShape).background(Colores.azul.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(Colores.superficieAlta), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = Colores.textoSuave, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(estado.numero, color = Colores.texto, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(estado.ubicacion, color = Colores.textoSuave, fontSize = 16.sp)
        Text("No está en tus contactos", color = Colores.textoSuave, fontSize = 14.sp)

        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Colores.superficie).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shield, null, tint = Colores.azul)
            Spacer(Modifier.width(10.dp))
            Text(
                "El asistente antifraude contestará en ${estado.cuentaRegresiva} s",
                color = Colores.texto,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BotonRedondo(Icons.Filled.CallEnd, "Rechazar", Colores.rojo, alRechazar)
            BotonRedondo(Icons.Filled.SupportAgent, "Asistente", Colores.azul, alAsistente)
            BotonRedondo(Icons.Filled.Call, "Contestar", Colores.verde, alContestar)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BotonRedondo(icono: ImageVector, etiqueta: String, color: Color, alTocar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = alTocar,
            modifier = Modifier.size(68.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = color, contentColor = Color.White),
        ) { Icon(icono, etiqueta, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(etiqueta, color = Colores.textoSuave, fontSize = 13.sp)
    }
}
